package ee.ria.eidas.proxy.specific;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.LightJAXBCodec;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.light.ILightResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.restassured.RestAssured;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import javax.cache.Cache;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ch.qos.logback.classic.Level.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest.OPENID_PROVIDER_WELL_KNOWN_PATH;
import static ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties.WebappProperties.DEFAULT_CONTENT_SECURITY_POLICY;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.apache.ignite.events.EventType.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;
import static org.slf4j.LoggerFactory.getLogger;

@Slf4j
@ActiveProfiles("test")
@Getter
public abstract class SpecificProxyTest {

    static {
        System.setProperty("javax.net.ssl.trustStore", "src/test/resources/__files/mock_keys/idp-tls-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "jks");
    }

    private static final Map<String, Object> EXPECTED_RESPONSE_HEADERS = new HashMap<String, Object>() {{
        put("X-XSS-Protection", "1; mode=block");
        put("X-Content-Type-Options", "nosniff");
        put("X-Frame-Options", "DENY");
        put("Content-Security-Policy", DEFAULT_CONTENT_SECURITY_POLICY);
        put("Pragma", "no-cache");
        put("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        //put("Strict-Transport-Security", "max-age=600000 ; includeSubDomains") // TODO: App must be running on https
    }};

    protected static final WireMockServer mockEidasNodeServer =
            new WireMockServer(WireMockConfiguration.wireMockConfig()
                    .httpDisabled(true)
                    .keystorePath("src/test/resources/__files/mock_keys/idp-tls-keystore.jks")
                    .keystorePassword("changeit")
                    .keyManagerPassword("changeit")
                    .httpsPort(8084)
            );

    protected static final WireMockServer mockOidcServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .httpDisabled(true)
            .httpsPort(9877)
            .keystorePath("src/test/resources/__files/mock_keys/idp-tls-keystore.jks")
            .keystorePassword("changeit")
            .keyManagerPassword("changeit")
    );

    protected static LightJAXBCodec codec = LightJAXBCodec.buildDefault();
    protected static Ignite eidasNodeIgnite;

    @Autowired
    protected SpecificProxyServiceProperties specificProxyServiceProperties;

    @Autowired
    protected SpecificProxyService specificProxyService;

    @MockBean
    protected BuildProperties buildProperties;

    @MockBean
    protected GitProperties gitProperties;

    @SpyBean
    protected MeterRegistry meterRegistry;

    @SpyBean
    protected Ignite igniteClient;

    @SpyBean
    protected Cache<String, SpecificProxyServiceCommunication.CorrelatedRequestsHolder> idpRequestCommunicationCache;

    @SpyBean
    protected Cache<String, ILightResponse> idpConsentCommunicationCache;

    @SpyBean
    @Qualifier("nodeSpecificProxyserviceRequestCache")
    protected Cache<String, String> eidasNodeRequestCommunicationCache;

    @SpyBean
    @Qualifier("nodeSpecificProxyserviceResponseCache")
    protected Cache<String, String> eidasNodeResponseCommunicationCache;

    private static ListAppender<ILoggingEvent> mockAppender;

    @LocalServerPort
    protected int port;

    @BeforeAll
    static void beforeAllTests() {
        startMockEidasNodeServer();
        startMockEidasNodeIgniteServer();
        startMockOidcServer();
        configureRestAssured();
    }

    @AfterAll
    static void afterAllTests() {
        mockOidcServer.stop();
        mockEidasNodeServer.stop();
    }

    @BeforeEach
    public void beforeEachTest() {
        RestAssured.responseSpecification = new ResponseSpecBuilder().expectHeaders(EXPECTED_RESPONSE_HEADERS).build();
        RestAssured.port = port;
        setupMockLogAppender();
    }

    @AfterEach
    public void afterEachTest() {
        ((Logger) getLogger(ROOT_LOGGER_NAME)).detachAppender(mockAppender);
    }

    @Test
    @Order(1)
    void contextLoads() {
        assertNotNull(specificProxyService, "Should not be null!");
    }

    protected static void startMockEidasNodeServer() {
        mockEidasNodeServer.start();
        mockEidasNodeServer.stubFor(get(urlEqualTo("/EidasNode/ServiceMetadata"))
                .willReturn(aResponse()
                        .withStatus(200)));
    }

    protected static void startMockOidcServer() {
        mockOidcServer.start();
        mockOidcServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(404)));
        mockOidcServer.stubFor(get(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/openid-configuration.json")));
    }

    protected void clearMockOidcServerMappings() {
        mockOidcServer.resetAll();
        mockOidcServer.stubFor(get(urlEqualTo("/oidc/jwks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/jwks.json")));
    }

    protected static void startMockEidasNodeIgniteServer() {
        if (eidasNodeIgnite == null) {
            System.setProperty("IGNITE_QUIET", "false");
            System.setProperty("IGNITE_HOME", System.getProperty("java.io.tmpdir"));
            System.setProperty("java.net.preferIPv4Stack", "true");
            InputStream cfgXml = SpecificProxyTest.class.getClassLoader()
                    .getResourceAsStream("mock_eidasnode/igniteSpecificCommunication.xml");
            IgniteConfiguration cfg = Ignition.loadSpringBean(cfgXml, "igniteSpecificCommunication.cfg");
            cfg.setIncludeEventTypes(EVT_CACHE_OBJECT_PUT, EVT_CACHE_OBJECT_READ, EVT_CACHE_OBJECT_REMOVED, EVT_CACHE_OBJECT_EXPIRED);
            eidasNodeIgnite = Ignition.getOrStart(cfg);
        }
    }

    protected static void configureRestAssured() {
        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
        RestAssured.config = config().redirect(redirectConfig().followRedirects(false));
    }

    protected void clearCommunicationCache() {
        idpRequestCommunicationCache.clear();
        eidasNodeResponseCommunicationCache.clear();
    }

    private void setupMockLogAppender() {
        mockAppender = new ListAppender<>();
        mockAppender.start();
        ((Logger) getLogger(ROOT_LOGGER_NAME)).addAppender(mockAppender);
    }

    protected void assertInfoIsLogged(String... messagesInRelativeOrder) {
        assertMessageIsLogged(null, INFO, messagesInRelativeOrder);
    }

    protected void assertWarningIsLogged(String... messagesInRelativeOrder) {
        assertMessageIsLogged(null, WARN, messagesInRelativeOrder);
    }

    protected void assertErrorIsLogged(String... messagesInRelativeOrder) {
        assertMessageIsLogged(null, ERROR, messagesInRelativeOrder);
    }

    protected void assertInfoIsLogged(Class<?> loggerClass, String... messagesInRelativeOrder) {
        assertMessageIsLogged(loggerClass, INFO, messagesInRelativeOrder);
    }

    protected void assertWarningIsLogged(Class<?> loggerClass, String... messagesInRelativeOrder) {
        assertMessageIsLogged(loggerClass, WARN, messagesInRelativeOrder);
    }

    protected void assertErrorIsLogged(Class<?> loggerClass, String... messagesInRelativeOrder) {
        assertMessageIsLogged(loggerClass, ERROR, messagesInRelativeOrder);
    }

    @SuppressWarnings("unchecked")
    private void assertMessageIsLogged(Class<?> loggerClass, Level loggingLevel,
                                       String... messagesInRelativeOrder) {
        List<String> events = mockAppender.list.stream()
                .filter(e -> e.getLevel() == loggingLevel && (loggerClass == null
                        || e.getLoggerName().equals(loggerClass.getCanonicalName())))
                .map(ILoggingEvent::getFormattedMessage)
                .collect(toList());

        assertThat(events, containsInRelativeOrder(stream(messagesInRelativeOrder)
                .map(CoreMatchers::startsWith).toArray(Matcher[]::new)));
    }

    public static class TestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(@NotNull ConfigurableApplicationContext configurableApplicationContext) {
            String currentDirectory = System.getProperty("user.dir");
            System.setProperty("SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/mock_eidasnode");
            System.setProperty("EIDAS_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/mock_eidasnode");
        }
    }
}

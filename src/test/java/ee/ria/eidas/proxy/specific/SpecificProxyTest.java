package ee.ria.eidas.proxy.specific;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.storage.EidasNodeCommunication;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.light.impl.LightRequest;
import eu.eidas.auth.commons.light.impl.LightResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import javax.cache.Cache;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.InputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.apache.ignite.events.EventType.*;

@Slf4j
@ActiveProfiles("test")
@Getter
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = SpecificProxyTest.TestContextInitializer.class)
public abstract class SpecificProxyTest {

    static {
        System.setProperty("javax.net.ssl.trustStore", "src/test/resources/__files/mock_keys/idp-tls-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "jks");
        try {
            codec = new EidasNodeCommunication.LightJAXBCodec(JAXBContext.newInstance(LightRequest.class, LightResponse.class,
                    ImmutableAttributeMap.class, AttributeDefinition.class));
        } catch (JAXBException e) {
            log.error("Unable to instantiate in static initializer ", e);
        }
    }

    protected static final WireMockServer mockEidasNodeServer =
            new WireMockServer(WireMockConfiguration.wireMockConfig()
                    .httpDisabled(true)
                    .keystorePath("src/test/resources/__files/mock_keys/idp-tls-keystore.jks")
                    .keystorePassword("changeit")
                    .httpsPort(8084)
            );

    protected static final WireMockServer mockOidcServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .httpDisabled(true)
            .httpsPort(9877)
            .keystorePath("src/test/resources/__files/mock_keys/idp-tls-keystore.jks")
            .keystorePassword("changeit")
    );

    protected static EidasNodeCommunication.LightJAXBCodec codec;
    protected static Ignite eidasNodeIgnite;

    @Autowired
    protected SpecificProxyServiceProperties specificProxyServiceProperties;

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
        RestAssured.port = port;
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
        mockOidcServer.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
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
            cfg.setIncludeEventTypes(EVT_CACHE_OBJECT_PUT, EVT_CACHE_OBJECT_READ, EVT_CACHE_OBJECT_REMOVED);
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

    public static class TestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(@NotNull ConfigurableApplicationContext configurableApplicationContext) {
            String currentDirectory = System.getProperty("user.dir");
            System.setProperty("SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/mock_eidasnode");
            System.setProperty("EIDAS_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/mock_eidasnode");
        }
    }
}

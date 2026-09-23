package ee.ria.eidas.proxy.specific.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest.OPENID_PROVIDER_WELL_KNOWN_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;
import static org.slf4j.LoggerFactory.getLogger;

@ActiveProfiles("test")
abstract class AbstractOidcProviderMetadataTest {

    static {
        System.setProperty("javax.net.ssl.trustStore", "src/test/resources/__files/mock_keys/idp-tls-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "jks");
    }

    protected static final WireMockServer mockOidcServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .httpDisabled(true)
            .httpsPort(9877)
            .keystorePath("src/test/resources/__files/mock_keys/idp-tls-keystore.jks")
            .keystorePassword("changeit")
            .keyManagerPassword("changeit")
    );

    @MockitoBean
    protected BuildProperties buildProperties;

    @MockitoBean
    protected GitProperties gitProperties;

    @MockitoSpyBean
    protected MeterRegistry meterRegistry;

    private static ListAppender<ILoggingEvent> mockAppender;

    @BeforeAll
    static void beforeAllTests() {
        mockOidcServer.start();
        mockOidcServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse().withStatus(404)));
        mockOidcServer.stubFor(get(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/openid-configuration.json")));
    }

    @AfterAll
    static void afterAllTests() {
        mockOidcServer.stop();
    }

    @BeforeEach
    void beforeEachTest() {
        mockAppender = new ListAppender<>();
        mockAppender.start();
        ((Logger) getLogger(ROOT_LOGGER_NAME)).addAppender(mockAppender);
    }

    @AfterEach
    void afterEachTest() {
        ((Logger) getLogger(ROOT_LOGGER_NAME)).detachAppender(mockAppender);
    }

    protected void assertInfoIsLogged(Class<?> loggerClass, String... messagesInRelativeOrder) {
        assertMessageIsLogged(loggerClass, Level.INFO, messagesInRelativeOrder);
    }

    protected void assertErrorIsLogged(Class<?> loggerClass, String... messagesInRelativeOrder) {
        assertMessageIsLogged(loggerClass, Level.ERROR, messagesInRelativeOrder);
    }

    private void assertMessageIsLogged(Class<?> loggerClass, Level loggingLevel, String... messagesInRelativeOrder) {
        List<String> events = mockAppender.list.stream()
                .filter(e -> e.getLevel() == loggingLevel && e.getLoggerName().equals(loggerClass.getCanonicalName()))
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());

        assertThat(events, containsInRelativeOrder(List.of(messagesInRelativeOrder).stream()
                .map(CoreMatchers::startsWith)
                .toArray(Matcher[]::new)));
    }
}

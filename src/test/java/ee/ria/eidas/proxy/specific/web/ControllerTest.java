package ee.ria.eidas.proxy.specific.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.LightJAXBCodec;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.impl.LightRequest;
import eu.eidas.auth.commons.light.impl.LightResponse;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.cache.Cache;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.*;
import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@Slf4j
@ActiveProfiles("test")
public abstract class ControllerTest {

    static {
        System.setProperty("javax.net.ssl.trustStore", "src/test/resources/__files/mock_keys/idp-tls-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "jks");


        try {
            codec = new LightJAXBCodec(JAXBContext.newInstance(LightRequest.class, LightResponse.class,
                    ImmutableAttributeMap.class, AttributeDefinition.class));
        } catch (JAXBException e) {
            log.error("Unable to instantiate in static initializer ",e);
        }
    }

    @LocalServerPort
    private int port;

    private static LightJAXBCodec codec;

    static WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .httpDisabled(true)
            .httpsPort(9877)
            .keystorePath("src/test/resources/__files/mock_keys/idp-tls-keystore.jks")
            .keystorePassword("changeit")
    );

    @Value("${lightToken.proxyservice.request.issuer.name}")
    private String lightTokenRequestIssuerName;

    @Value("${lightToken.proxyservice.request.secret}")
    private String lightTokenRequestSecret;

    @Value("${lightToken.proxyservice.request.algorithm}")
    private String lightTokenRequestAlgorithm;

    @Value("${lightToken.proxyservice.response.issuer.name}")
    private String lightTokenResponseIssuerName;

    @Autowired
    @Qualifier("nodeSpecificProxyserviceRequestCache")
    @Getter
    private Cache<String, String> eidasNodeRequestCommunicationCache;

    @Autowired
    @Qualifier("nodeSpecificProxyserviceResponseCache")
    @Getter
    private Cache<String, String> eidasNodeResponseCommunicationCache;

    @Autowired
    @Getter
    private Cache<String, SpecificProxyServiceCommunication.CorrelatedRequestsHolder> idpRequestCommunicationCache;

    @Autowired
    @Getter
    private SpecificProxyService specificProxyService;

    @Autowired
    @Getter
    private SpecificProxyServiceCommunication specificProxyServiceCommunication;

    @Autowired
    @Getter
    private SpecificProxyServiceProperties specificProxyServiceProperties;

    @Mock
    @Getter
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    @Getter
    private ArgumentCaptor<LoggingEvent> captorLoggingEvent;

    @BeforeAll
    public static void beforeAllTests() {
        startMockOidcServer();
        configureRestAssured();
    }

    @BeforeEach
    public void startTest() {
        clearMockOidcServerMappings();
        clearCommunicationCache();
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(mockAppender);
        RestAssured.port = port;
    }

    @AfterEach
    public void stopTest() {
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.detachAppender(mockAppender);
    }

    @AfterAll
    public static void afterAllTests() {
        stopMockOidcServer();
    }

    private void clearMockOidcServerMappings() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(get(urlEqualTo("/oidc/jwks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/jwks.json")));
    }



    @Test
    @Order(1)
    void contextLoads() {
        assertNotNull(specificProxyService, "Should not be null!");
    }

    BinaryLightToken putRequest(final ILightRequest iLightRequest) throws SpecificCommunicationException {

        final BinaryLightToken binaryLightToken = BinaryLightTokenHelper.createBinaryLightToken(
                lightTokenRequestIssuerName,
                lightTokenRequestSecret,
                lightTokenRequestAlgorithm);
        final String tokenId = binaryLightToken.getToken().getId();
        getEidasNodeRequestCommunicationCache().put(tokenId, codec.marshall(iLightRequest));
        return binaryLightToken;
    }

    void assertRequestCommunicationCacheIsEmpty() {
        List<Cache.Entry<String, String>> list = getListFromIterator(getEidasNodeRequestCommunicationCache().iterator());
        assertEquals(0, list.size());
    }

    void assertPendingIdpRequestCommunicationCacheIsEmpty() {
        List<Cache.Entry<String, SpecificProxyServiceCommunication.CorrelatedRequestsHolder>> list = getListFromIterator(getIdpRequestCommunicationCache().iterator());
        assertEquals(0, list.size());
    }

    void assertResponseCommunicationCacheIsEmpty() {
        List<Cache.Entry<String, String>> list = getListFromIterator(getEidasNodeResponseCommunicationCache().iterator());
        assertEquals(0, list.size());
    }


    void assertErrorIsLogged(String errorMessage) {
        verify(getMockAppender(), atLeast(1)).doAppend(getCaptorLoggingEvent().capture());

        LoggingEvent lastLoggingEvent = getCaptorLoggingEvent().getValue();
        assertThat(lastLoggingEvent.getLevel(), equalTo(Level.ERROR));
        assertThat(lastLoggingEvent.getMessage(), startsWith(errorMessage));
    }

    void assertHttpMethodsNotAllowed(String path, String... restrictedMethods) {
        for (String method : restrictedMethods) {
            given()
            .when().
                request(method, path).
            then()
                .assertThat()
                .statusCode(405);
        }
    }

    void assertResponseCommunicationCacheContainsUserCancelResponse(String expectedErrorMessage, String expectedInResponseTo) throws SAXException, IOException, ParserConfigurationException {
        List<Cache.Entry<String, String>> list = getListFromIterator(getEidasNodeResponseCommunicationCache().iterator());
        assertEquals(1, list.size());
        assertThat(list.get(0).getKey(), matchesPattern(UUID_REGEX));

        Element responseXml = getXmlDocument(list.get(0).getValue());
        assertThat(responseXml, hasXPath("/lightResponse/id", matchesPattern(UUID_REGEX)));
        assertThat(responseXml, hasXPath("/lightResponse/inResponseToId", equalTo(expectedInResponseTo)));
        assertThat(responseXml, hasXPath("/lightResponse/issuer", equalTo(lightTokenResponseIssuerName)));
        assertThat(responseXml, hasXPath("/lightResponse/status/failure", equalTo("true")));
        assertThat(responseXml, hasXPath("/lightResponse/status/statusMessage", equalTo(expectedErrorMessage)));
        assertThat(responseXml, hasXPath("/lightResponse/status/subStatusCode", equalTo("urn:oasis:names:tc:SAML:2.0:status:RequestDenied")));
        assertThat(responseXml, hasXPath("count(/lightResponse/attributes/attribute)", equalTo("0")));
    }

    public static class TestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            String currentDirectory = System.getProperty("user.dir");
            System.setProperty("SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/config/tomcat/specificProxyService");
            System.setProperty("EIDAS_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/config/tomcat");
        }
    }

    private static void configureRestAssured() {
        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
        RestAssured.config = config().redirect(redirectConfig().followRedirects(false));
    }

    private static void startMockOidcServer() {
        wireMockServer.start();
        wireMockServer.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/openid-configuration.json")));
    }

    private static void stopMockOidcServer() {
        wireMockServer.stop();
    }

    private void clearCommunicationCache() {
        idpRequestCommunicationCache.clear();
        eidasNodeResponseCommunicationCache.clear();
    }
}

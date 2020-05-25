package ee.ria.eidas.proxy.specific.web;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.StoredMSProxyServiceRequestCorrelationMap;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.response.ValidatableResponse;
import lombok.Getter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import javax.cache.Cache;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.getListFromIterator;
import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("test")
public abstract class ControllerTest {

    static {
        System.setProperty("javax.net.ssl.trustStore", "src/test/resources/__files/mock_keys/idp-tls-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "jks");
    }

    @LocalServerPort
    private int port;

    private static WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .httpDisabled(true)
            .httpsPort(9877)
            .keystorePath("src/test/resources/__files/mock_keys/idp-tls-keystore.jks")
            .keystorePassword("changeit")
    );


    @Autowired
    @Qualifier("nodeSpecificProxyserviceResponseCache")
    @Getter
    private Cache<String, String> eidasNodeResponseCommunicationCache;

    @Autowired
    @Getter
    private Cache<String, StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder> eidasNodeRequestCommunicationCache;

    @Autowired
    @Getter
    private SpecificProxyService specificProxyService;

    @Autowired
    @Getter
    private SpecificProxyServiceProperties specificProxyServiceProperties;

    @BeforeAll
    public static void setupAll() {
        configureRestAssured();
        configureMockOidcServer();
    }

    @Test
    @Order(1)
    void contextLoads() {
        assertNotNull(specificProxyService, "Should not be null!");
    }

    void assertResponseCommunicationCacheIsEmpty() {
        List<Cache.Entry<String, String>> list = getListFromIterator(getEidasNodeResponseCommunicationCache().iterator());
        assertEquals(0, list.size());
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

    public static class TestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            String currentDirectory = System.getProperty("user.dir");
            System.setProperty("SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/config/tomcat/specificProxyService");
            System.setProperty("EIDAS_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/config/tomcat");
        }
    }

    private static void configureMockOidcServer() {
        wireMockServer.start();

        wireMockServer.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/openid-configuration.json")));

        wireMockServer.stubFor(post(urlEqualTo("/oidc/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/token-response.json")));

        wireMockServer.stubFor(get(urlEqualTo("/oidc/jwks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/jwks.json")));
    }

    private static void configureRestAssured() {

        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
        RestAssured.config = config().redirect(redirectConfig().followRedirects(false));
    }

    @BeforeEach
    public void start() {
        RestAssured.port = port;
        eidasNodeRequestCommunicationCache.clear();
        eidasNodeResponseCommunicationCache.clear();
    }

    @AfterAll
    public static void stop() {
        wireMockServer.stop();
    }
}

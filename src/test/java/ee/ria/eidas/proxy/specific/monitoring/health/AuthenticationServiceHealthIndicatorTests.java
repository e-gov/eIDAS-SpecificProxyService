package ee.ria.eidas.proxy.specific.monitoring.health;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.monitoring.ApplicationHealthEndpointTests;
import ee.ria.eidas.proxy.specific.monitoring.ApplicationHealthTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest.OPENID_PROVIDER_WELL_KNOWN_PATH;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.net.HttpURLConnection.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT,
        properties = {
                "management.endpoints.jmx.exposure.exclude=*",
                "management.endpoints.web.exposure.include=heartbeat",
                "management.endpoints.web.base-path=/",
                "management.info.git.mode=full",
                "management.health.defaults.enabled=false",
                "eidas.proxy.health.trust-store-expiration-warning=30d"
        })
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = ApplicationHealthEndpointTests.TestContextInitializer.class)
public class AuthenticationServiceHealthIndicatorTests extends ApplicationHealthTest {


    @Test
    public void healthStatusUpWhenStatus200() {

        mockOidcServer.stubFor(get(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH))
                .willReturn(aResponse()
                        .withStatus(HTTP_OK)));
        Response healthResponse = getHealthResponse();
        assertDependenciesUp(healthResponse);
    }

    @Test
    public void healthStatusDownWhenStatus404() {
        mockOidcServer.stubFor(get(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH))
                .willReturn(aResponse()
                        .withStatus(HTTP_NOT_FOUND)));
        Response healthResponse = getHealthResponse();
        assertDependenciesDown(healthResponse, Dependencies.AUTHENTICATION_SERVICE);
    }

    @Test
    public void healthStatusDownWhenConnectionRefused() {
        mockOidcServer.stop();
        Response healthResponse = getHealthResponse();
        mockOidcServer.start();
        assertDependenciesDown(healthResponse, Dependencies.AUTHENTICATION_SERVICE);
    }

    @Test
    public void healthStatusDownWhenStatus500() {
        mockOidcServer.stubFor(get(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH))
                .willReturn(aResponse()
                        .withStatus(HTTP_INTERNAL_ERROR)));
        Response healthResponse = getHealthResponse();
        assertDependenciesDown(healthResponse, Dependencies.AUTHENTICATION_SERVICE);
    }
}

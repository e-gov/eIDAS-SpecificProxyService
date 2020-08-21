package ee.ria.eidas.proxy.specific.monitoring.health;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.monitoring.ApplicationHealthEndpointTests;
import ee.ria.eidas.proxy.specific.monitoring.ApplicationHealthTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
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
public class ProxyServiceMetadataHealthIndicatorTests extends ApplicationHealthTest {

    @Test
    public void healthStatusDownWhenEidasNodeDown() {
        mockEidasNodeServer.stubFor(get(urlEqualTo("/EidasNode/ServiceMetadata"))
                .willReturn(aResponse()
                        .withStatus(404)));
        Response healthResponse = getHealthResponse();
        assertDependenciesDown(healthResponse, Dependencies.PROXY_SERVICE_METADATA);
    }
}

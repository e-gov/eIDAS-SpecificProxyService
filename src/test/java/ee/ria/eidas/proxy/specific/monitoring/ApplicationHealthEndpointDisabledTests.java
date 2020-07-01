package ee.ria.eidas.proxy.specific.monitoring;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static io.restassured.RestAssured.given;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT,
        properties = {
                "management.endpoints.jmx.exposure.exclude=*",
                "management.endpoints.web.exposure.exclude=*",
                "management.endpoints.web.base-path=/",
                "management.info.git.mode=full",
                "management.health.defaults.enabled=false",
                "eidas.proxy.health.trust-store-expiration-warning=30d"
        })
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = ApplicationHealthEndpointDisabledTests.TestContextInitializer.class)
class ApplicationHealthEndpointDisabledTests extends ApplicationHealthTest {

    @Test
    public void healthEndpointNotAccessible() {
        given()
                .when()
                .get(APPLICATION_HEALTH_ENDPOINT_REQUEST)
                .then()
                .assertThat()
                .statusCode(404);
    }
}

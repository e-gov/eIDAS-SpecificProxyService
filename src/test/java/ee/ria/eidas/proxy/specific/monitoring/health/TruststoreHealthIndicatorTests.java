package ee.ria.eidas.proxy.specific.monitoring.health;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.monitoring.ApplicationHealthTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.*;
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
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = ApplicationHealthTest.TestContextInitializer.class)
class TruststoreHealthIndicatorTests extends ApplicationHealthTest {

    @SpyBean
    TruststoreHealthIndicator truststoreHealthIndicator;

    @Test
    public void noTruststoreWarningsWhenWarningPeriodNotMet() {
        Mockito.when(truststoreHealthIndicator.getCurrentTime()).thenReturn(Instant.parse("2021-04-13T08:50:00Z"));
        Response healthResponse = given()
                .when()
                .get(APPLICATION_HEALTH_ENDPOINT_REQUEST)
                .then()
                .assertThat()
                .statusCode(200)
                .contentType(JSON).extract().response();

        List<String> warnings = healthResponse.jsonPath().getList("warnings");
        assertNull(warnings);
        assertDependenciesUp(healthResponse);
    }

    @Test
    public void truststoreWarningWhenCertificateAboutToExpire() {
        Mockito.when(truststoreHealthIndicator.getCurrentTime()).thenReturn(Instant.parse("2021-04-14T08:50:00Z"));
        Response healthResponse = given()
                .when()
                .get(APPLICATION_HEALTH_ENDPOINT_REQUEST)
                .then()
                .assertThat()
                .statusCode(200)
                .contentType(JSON).extract().response();

        List<String> warnings = healthResponse.jsonPath().getList("warnings");
        assertNotNull(warnings);
        Optional<String> authenticationService = warnings.stream()
                .filter(w -> w.contains("1589359800"))
                .findFirst();
        assertEquals("Truststore certificate 'CN=localhost, OU=test, O=test, L=test, ST=test, C=EE' with serial number '1589359800' is expiring at 2021-05-13T08:50:00Z", authenticationService.get());
        assertDependenciesUp(healthResponse);
    }

    @Test
    public void truststoreWarningAndHealthStatusDownWhenCertificateExpired() {
        Mockito.when(truststoreHealthIndicator.getCurrentTime()).thenReturn(Instant.parse("2021-05-13T08:51:00Z"));
        Response healthResponse = given()
                .when()
                .get(APPLICATION_HEALTH_ENDPOINT_REQUEST)
                .then()
                .assertThat()
                .statusCode(200)
                .contentType(JSON).extract().response();

        List<String> warnings = healthResponse.jsonPath().getList("warnings");
        assertNotNull(warnings);
        Optional<String> authenticationService = warnings.stream()
                .filter(w -> w.contains("1589359800"))
                .findFirst();
        assertEquals("Truststore certificate 'CN=localhost, OU=test, O=test, L=test, ST=test, C=EE' with serial number '1589359800' is expiring at 2021-05-13T08:50:00Z", authenticationService.get());
        assertDependenciesDown(healthResponse, Dependencies.TRUSTSTORE);
    }
}

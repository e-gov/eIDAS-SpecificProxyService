package ee.ria.eidas.proxy.specific.monitoring.health;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.monitoring.ApplicationHealthTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.time.ZoneId.of;
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
        Instant mockSystemTime = Instant.parse("2031-04-07T14:22:10Z");
        Mockito.when(truststoreHealthIndicator.getSystemClock()).thenReturn(Clock.fixed(mockSystemTime, of("UTC")));
        Response healthResponse = getHealthResponse();

        List<String> warnings = healthResponse.jsonPath().getList("warnings");
        assertNull(warnings);
        assertDependenciesUp(healthResponse);
    }

    @Test
    public void truststoreWarningWhenCertificateAboutToExpire() {
        Instant mockSystemTime = Instant.parse("2031-04-07T14:23:10Z");
        Mockito.when(truststoreHealthIndicator.getSystemClock()).thenReturn(Clock.fixed(mockSystemTime, of("UTC")));
        Response healthResponse = getHealthResponse();

        List<String> warnings = healthResponse.jsonPath().getList("warnings");
        assertNotNull(warnings);
        Optional<String> authenticationService = warnings.stream()
                .filter(w -> w.contains("1620397330"))
                .findFirst();
        assertEquals("Truststore certificate 'CN=localhost,OU=test,O=test,L=test,ST=test,C=EE' with serial number '1620397330' is expiring at 2031-05-07T14:22:10Z", authenticationService.get());
        assertDependenciesUp(healthResponse);
    }

    @Test
    public void truststoreWarningAndHealthStatusDownWhenCertificateExpired() {
        Instant mockSystemTime = Instant.parse("2031-05-07T14:23:10Z");
        Mockito.when(truststoreHealthIndicator.getSystemClock()).thenReturn(Clock.fixed(mockSystemTime, of("UTC")));
        Response healthResponse = getHealthResponse();

        List<String> warnings = healthResponse.jsonPath().getList("warnings");
        assertNotNull(warnings);
        Optional<String> authenticationService = warnings.stream()
                .filter(w -> w.contains("1620397330"))
                .findFirst();
        assertEquals("Truststore certificate 'CN=localhost,OU=test,O=test,L=test,ST=test,C=EE' with serial number '1620397330' is expiring at 2031-05-07T14:22:10Z", authenticationService.get());
        assertDependenciesDown(healthResponse, Dependencies.TRUSTSTORE);
    }
}

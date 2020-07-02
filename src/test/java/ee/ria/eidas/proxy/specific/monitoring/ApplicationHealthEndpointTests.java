package ee.ria.eidas.proxy.specific.monitoring;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import io.micrometer.core.instrument.search.Search;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
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
public class ApplicationHealthEndpointTests extends ApplicationHealthTest {

    @Test
    public void healthyApplicationState() {
        Instant testTime = Instant.now();
        when(gitProperties.getCommitId()).thenReturn("commit-id");
        when(gitProperties.getBranch()).thenReturn("branch");
        when(buildProperties.getName()).thenReturn("ee-specific-proxy");
        when(buildProperties.getVersion()).thenReturn("0.0.1-SNAPSHOT");
        when(buildProperties.getTime()).thenReturn(testTime);

        Response healthResponse = given()
                .when()
                .get(APPLICATION_HEALTH_ENDPOINT_REQUEST)
                .then()
                .assertThat()
                .statusCode(200)
                .contentType(JSON).extract().response();

        assertEquals("UP", healthResponse.jsonPath().get("status"));
        assertEquals("ee-specific-proxy", healthResponse.jsonPath().get("name"));
        assertEquals("0.0.1-SNAPSHOT", healthResponse.jsonPath().get("version"));
        assertEquals(testTime.toString(), healthResponse.jsonPath().get("buildTime"));
        assertEquals("commit-id", healthResponse.jsonPath().get("commitId"));
        assertEquals("branch", healthResponse.jsonPath().get("commitBranch"));
        assertNull(healthResponse.jsonPath().get("warnings"));
        assertStartAndUptime(healthResponse);
        assertDependenciesUp(healthResponse);
    }

    @Test
    public void healthyApplicationStateWhenMissingBuildAndGitInfo() {
        when(gitProperties.getCommitId()).thenReturn(null);
        when(gitProperties.getBranch()).thenReturn(null);
        when(buildProperties.getName()).thenReturn(null);
        when(buildProperties.getVersion()).thenReturn(null);
        when(buildProperties.getTime()).thenReturn(null);

        Response healthResponse = given()
                .when()
                .get(APPLICATION_HEALTH_ENDPOINT_REQUEST)
                .then()
                .assertThat()
                .statusCode(200)
                .contentType(JSON).extract().response();

        assertEquals("UP", healthResponse.jsonPath().get("status"));
        assertNull(healthResponse.jsonPath().get("commitId"));
        assertNull(healthResponse.jsonPath().get("commitBranch"));
        assertNull(healthResponse.jsonPath().get("name"));
        assertNull(healthResponse.jsonPath().get("version"));
        assertNull(healthResponse.jsonPath().get("buildTime"));
        assertNull(healthResponse.jsonPath().get("warnings"));
        assertStartAndUptime(healthResponse);
        assertDependenciesUp(healthResponse);
    }

    @Test
    public void healthyApplicationStateWhenMissingMetrics() {
        Search nonExistentMetric = meterRegistry.find("non-existent");
        Mockito.when(meterRegistry.find("process.start.time")).thenReturn(nonExistentMetric);
        Mockito.when(meterRegistry.find("process.uptime")).thenReturn(nonExistentMetric);
        Response healthResponse = given()
                .when()
                .get(APPLICATION_HEALTH_ENDPOINT_REQUEST)
                .then()
                .assertThat()
                .statusCode(200)
                .contentType(JSON).extract().response();
        assertNull(healthResponse.jsonPath().get("startTime"));
        assertNull(healthResponse.jsonPath().get("upTime"));
        assertDependenciesUp(healthResponse);
    }
}

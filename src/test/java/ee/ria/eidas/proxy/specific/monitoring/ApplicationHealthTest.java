package ee.ria.eidas.proxy.specific.monitoring;

import ee.ria.eidas.proxy.specific.SpecificProxyTest;
import io.micrometer.core.instrument.TimeGauge;
import io.restassured.response.Response;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import static java.lang.Double.valueOf;
import static java.time.Instant.ofEpochMilli;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

public abstract class ApplicationHealthTest extends SpecificProxyTest {
    protected static final String APPLICATION_HEALTH_ENDPOINT_REQUEST = "/heartbeat";

    protected static void setClusterStateInactive() {
        eidasNodeIgnite.cluster().active(false);
    }

    protected static void setClusterStateActive() {
        eidasNodeIgnite.cluster().active(true);
    }

    protected void assertDependenciesUp(Response healthResponse) {
        assertEquals("UP", healthResponse.jsonPath().get("status"));
        List<HashMap<String, String>> healthDependencies = healthResponse.jsonPath().getList("dependencies");
        assertNotNull(healthDependencies);
        List<String> dependencies = healthDependencies.stream().map(d -> d.get("name")).collect(toList());
        stream(Dependencies.values()).forEach(d -> assertTrue(dependencies.contains(d.getName())));

        healthDependencies.stream()
                .map(d -> d.get("status"))
                .forEach(status -> assertEquals("UP", status));
    }

    protected void assertDependenciesDown(Response healthResponse, Dependencies... dependenciesDown) {
        assertEquals("DOWN", healthResponse.jsonPath().get("status"));
        List<HashMap<String, String>> healthDependencies = healthResponse.jsonPath().getList("dependencies");
        assertNotNull(healthDependencies);
        List<String> dependencies = healthDependencies.stream().map(d -> d.get("name")).collect(toList());
        stream(Dependencies.values()).forEach(d -> assertTrue(dependencies.contains(d.getName())));

        List<Dependencies> dependenciesDownList = asList(dependenciesDown);
        healthDependencies.stream()
                .filter(s -> dependenciesDownList.contains(s.get("name")))
                .map(d -> d.get("status"))
                .forEach(status -> assertEquals("DOWN", status));
    }

    protected void assertStartAndUptime(Response healthResponse) {
        Instant startTime = Instant.parse(healthResponse.jsonPath().get("startTime"));
        TimeGauge startTimeGauge = meterRegistry.find("process.start.time").timeGauge();
        assertEquals(ofEpochMilli(valueOf(startTimeGauge.value(MILLISECONDS)).longValue()), startTime);

        Instant currentTime = Instant.parse(healthResponse.jsonPath().get("currentTime"));
        assertTrue(currentTime.isAfter(startTime));
        assertTrue(currentTime.isBefore(Instant.now()));

        Duration upTime = Duration.parse(healthResponse.jsonPath().get("upTime"));
        assertEquals(Duration.between(startTime, currentTime).withNanos(0), upTime);
    }

    @RequiredArgsConstructor
    public enum Dependencies {
        AUTHENTICATION_SERVICE("authenticationService"),
        IGNITE_CLUSTER("igniteCluster"),
        PROXY_SERVICE_METADATA("proxyServiceMetadata"),
        TRUSTSTORE("truststore");
        @Getter
        public final String name;
    }
}

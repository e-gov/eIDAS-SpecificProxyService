package ee.ria.eidas.proxy.specific.storage;

import ee.ria.eidas.proxy.specific.SpecificProxyTest;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import eu.eidas.auth.commons.light.ILightResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.lang.IgnitePredicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.ContextConfiguration;

import javax.cache.Cache;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.ignite.events.EventType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = IgniteInstanceInitializerTests.TestContextInitializer.class)
public class IgnateCacheExpirationTests extends SpecificProxyTest {
    private static final long EVENT_TIMEOUT = 3_000;

    @BeforeAll
    static void startIgnite() {
        stopIgnite();
        startMockEidasNodeIgniteServer("mock_eidasnode/igniteSpecificCommunication_fastExpiration.xml");
    }

    @AfterAll
    static void stopIgnite() {
        Ignition.stopAll(true);
        eidasNodeIgnite = null;
    }

    @ParameterizedTest
    @ValueSource(strings = {"nodeSpecificProxyserviceRequestCache",
            "specificNodeProxyserviceResponseCache",
            "specificMSIdpRequestCorrelationMap",
            "specificMSIdpConsentCorrelationMap"})
    void cacheEventExpires(String cacheName) throws Exception {
        CountDownLatch objectExpiredLatch = new CountDownLatch(1);
        AtomicReference<CacheEvent> expiredEvent = new AtomicReference<>();
        IgnitePredicate<CacheEvent> localListener = evt -> {
            log.debug("Ignite client event: {}/{}/{}  Cache: {}", evt.type(), evt.key(), evt.oldValue(), evt.cacheName());
            if (evt.type() == EVT_CACHE_OBJECT_EXPIRED) {
                expiredEvent.set(evt);
                objectExpiredLatch.countDown();
            }
            return true;
        };

        eidasNodeIgnite.events().localListen(localListener, EVT_CACHE_OBJECT_EXPIRED);
        eidasNodeIgnite.getOrCreateCache(cacheName).put(cacheName, "testValue");
        assertExpirationEvent(objectExpiredLatch);

        CacheEvent evt = expiredEvent.get();
        assertEquals(cacheName, evt.key());
        assertEquals("testValue", evt.oldValue());
        eidasNodeIgnite.events().stopLocalListen(localListener);
    }

    private void assertExpirationEvent(CountDownLatch latch) throws Exception {
        if (!latch.await(EVENT_TIMEOUT, MILLISECONDS)) {
            fail("Failed to wait for disconnect/reconnect event.");
        }
    }
}

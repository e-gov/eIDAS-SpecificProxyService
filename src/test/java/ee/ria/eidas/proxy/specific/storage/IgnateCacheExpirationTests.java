package ee.ria.eidas.proxy.specific.storage;

import ee.ria.eidas.proxy.specific.SpecificProxyTest;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.lang.IgnitePredicate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import javax.cache.Cache;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.ignite.events.EventType.EVT_CACHE_OBJECT_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = IgniteInstanceInitializerTests.TestContextInitializer.class)
public class IgnateCacheExpirationTests extends SpecificProxyTest {
    private static final long EVENT_TIMEOUT = 4_000;

    @Test
    void nodeSpecificProxyserviceRequestCacheEventExpires() {
        cacheEventExpires(eidasNodeRequestCommunicationCache);
    }

    @Test
    void nodeSpecificProxyserviceResponseCacheEventExpires() {
        cacheEventExpires(eidasNodeResponseCommunicationCache);
    }

    @Test
    void specificMSIdpRequestCorrelationMapEventExpires() {
        cacheEventExpires(idpRequestCommunicationCache);
    }

    @Test
    void specificMSIdpConsentCorrelationMapEventExpires() {
        cacheEventExpires(idpConsentCommunicationCache);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void cacheEventExpires(Cache cache) {
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
        cache.put(cache.getName(), "testValue");
        assertExpirationEvent(objectExpiredLatch);
        CacheEvent evt = expiredEvent.get();
        assertEquals(cache.getName(), evt.key());
        assertEquals("testValue", evt.oldValue());
        eidasNodeIgnite.events().stopLocalListen(localListener);
    }

    @SneakyThrows
    private void assertExpirationEvent(CountDownLatch latch) {
        if (!latch.await(EVENT_TIMEOUT, MILLISECONDS)) {
            fail("Failed to wait for object expired event.");
        }
    }
}

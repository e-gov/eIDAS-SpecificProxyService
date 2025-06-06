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
import java.util.UUID;
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
public class IgniteCacheExpirationTests extends SpecificProxyTest {
    private static final long EVENT_TIMEOUT = 10_000;

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
        String cacheEntryKey = "testKey-" + UUID.randomUUID();
        CountDownLatch objectExpiredLatch = new CountDownLatch(1);
        AtomicReference<CacheEvent> expiredEvent = new AtomicReference<>();
        IgnitePredicate<CacheEvent> localListener = evt -> {
            log.debug("Ignite client event: {}/{}/{}  Cache: {}", evt.type(), evt.key(), evt.oldValue(), evt.cacheName());
            if (!evt.cacheName().equals(cache.getName())) {
                return true;
            }
            if (evt.type() != EVT_CACHE_OBJECT_EXPIRED) {
                return true;
            }
            if (!evt.key().equals(cacheEntryKey)) {
                return true;
            }
            expiredEvent.set(evt);
            objectExpiredLatch.countDown();
            return true;
        };

        eidasNodeIgnite.events().localListen(localListener, EVT_CACHE_OBJECT_EXPIRED);
        cache.put(cacheEntryKey, "testValue");
        assertExpirationEvent(objectExpiredLatch);
        assertEquals("testValue", expiredEvent.get().oldValue());
        eidasNodeIgnite.events().stopLocalListen(localListener);
    }

    @SneakyThrows
    private void assertExpirationEvent(CountDownLatch latch) {
        if (!latch.await(EVENT_TIMEOUT, MILLISECONDS)) {
            fail("Failed to wait for object expired event.");
        }
    }
}

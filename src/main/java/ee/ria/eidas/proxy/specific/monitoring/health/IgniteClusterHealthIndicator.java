package ee.ria.eidas.proxy.specific.monitoring.health;

import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication.CorrelatedRequestsHolder;
import eu.eidas.auth.commons.light.ILightResponse;
import org.apache.ignite.Ignite;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import java.util.UUID;

@Component
public class IgniteClusterHealthIndicator extends AbstractHealthIndicator {

    @Lazy
    @Autowired
    private Ignite igniteClient;

    @Lazy
    @Autowired
    private Cache<String, CorrelatedRequestsHolder> idpRequestCommunicationCache;

    @Lazy
    @Autowired
    private Cache<String, ILightResponse> idpConsentCommunicationCache;

    @Lazy
    @Autowired
    @Qualifier("nodeSpecificProxyserviceRequestCache")
    private Cache<String, String> eidasNodeRequestCommunicationCache;

    @Lazy
    @Autowired
    @Qualifier("nodeSpecificProxyserviceResponseCache")
    private Cache<String, String> eidasNodeResponseCommunicationCache;

    public IgniteClusterHealthIndicator() {
        super("Ignite cluster health check failed");
    }

    @Override
    protected void doHealthCheck(Health.@NonNull Builder builder) {
        if (igniteClient.cluster().active()
                && isCacheHealthy(eidasNodeRequestCommunicationCache)
                && isCacheHealthy(eidasNodeResponseCommunicationCache)
                && isCacheHealthy(idpRequestCommunicationCache)
                && isCacheHealthy(idpConsentCommunicationCache)) {
            builder.up().build();
        } else {
            builder.down().build();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean isCacheHealthy(Cache cache) {
        String uuid = UUID.randomUUID().toString();
        cache.put(uuid, uuid);
        return uuid.equals(cache.getAndRemove(uuid));
    }
}

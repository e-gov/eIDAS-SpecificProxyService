package ee.ria.eidas.proxy.specific.storage;

import eu.eidas.auth.commons.cache.ConcurrentCacheService;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.tx.AbstractCache;
import lombok.Getter;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

/**
 * Default implementation of the CorrelationMap for specific {@link CorrelatedRequestsHolder} instances.
 *
 * @since 2.0
 */
public final class StoredMSProxyServiceRequestCorrelationMap extends AbstractCache<String, StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder> {

    public StoredMSProxyServiceRequestCorrelationMap(final ConcurrentCacheService concurrentCacheService) {
        super(concurrentCacheService);
    }

    /**
     * Holds the light request and the correlated specific request.
     */
    public static class CorrelatedRequestsHolder implements Serializable {

        private static final long serialVersionUID = 8942548697342198159L;

        @Getter
        private ILightRequest iLightRequest;

        @Getter
        private Map<String, URI> authenticationRequest;

        public CorrelatedRequestsHolder(ILightRequest iLightRequest, Map<String, URI> authenticationRequest) {
            this.iLightRequest = iLightRequest;
            this.authenticationRequest = authenticationRequest;
        }
    }
}
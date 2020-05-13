package ee.ria.eidas.proxy.specific.storage;

import eu.eidas.auth.commons.cache.ConcurrentCacheService;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.tx.AbstractCache;
import lombok.Getter;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

public final class StoredMSProxyServiceConsentCorrelationMap extends AbstractCache<String, ILightResponse> {

    public StoredMSProxyServiceConsentCorrelationMap(final ConcurrentCacheService concurrentCacheService) {
        super(concurrentCacheService);
    }
}
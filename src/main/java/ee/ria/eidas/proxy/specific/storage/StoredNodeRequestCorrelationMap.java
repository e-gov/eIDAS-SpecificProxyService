package ee.ria.eidas.proxy.specific.storage;

import eu.eidas.auth.commons.cache.ConcurrentCacheService;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.tx.AbstractCache;
import lombok.Getter;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

public final class StoredNodeRequestCorrelationMap extends AbstractCache<String, String> {

    public StoredNodeRequestCorrelationMap(final ConcurrentCacheService concurrentCacheService) {
        super(concurrentCacheService);
    }
}
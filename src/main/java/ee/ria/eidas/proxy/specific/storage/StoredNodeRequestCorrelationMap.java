package ee.ria.eidas.proxy.specific.storage;

import eu.eidas.auth.commons.cache.ConcurrentCacheService;
import eu.eidas.auth.commons.tx.AbstractCache;

public final class StoredNodeRequestCorrelationMap extends AbstractCache<String, String> {

    public StoredNodeRequestCorrelationMap(final ConcurrentCacheService concurrentCacheService) {
        super(concurrentCacheService);
    }
}
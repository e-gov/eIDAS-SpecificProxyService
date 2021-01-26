package ee.ria.eidas.proxy.specific.storage;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class IgniteInstanceInitializer {

    private final SpecificProxyServiceProperties.CacheProperties properties;
    private final ResourceLoader resourceLoader;

    private static Ignite instance;

    public synchronized void initializeInstance() throws IOException {
        if (null == instance) {
            Resource resource = getResource(properties, resourceLoader, properties.getIgniteConfigurationFileLocation());

            Ignition.setClientMode(true);
            IgniteConfiguration cfg = Ignition.loadSpringBean(resource.getInputStream(), properties.getIgniteConfigurationBeanName());
            cfg.setIgniteInstanceName(cfg.getIgniteInstanceName() + "Client");
            cfg.setGridLogger(new Slf4jLogger());
            instance = Ignition.start(cfg);
        }
    }

    public synchronized Ignite getInstance() {
        return instance;
    }

    private Resource getResource(SpecificProxyServiceProperties.CacheProperties properties, ResourceLoader resourceLoader, String resourceLocation) {
        Resource resource = resourceLoader.getResource(resourceLocation);
        if (!resource.exists())
            throw new IllegalStateException("Required configuration file not found: " + properties.getIgniteConfigurationFileLocation());
        return resource;
    }
}
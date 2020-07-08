package ee.ria.eidas.proxy.specific.config;

import ee.ria.eidas.proxy.specific.service.OIDCProviderMetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@ConditionalOnProperty(value = "eidas.proxy.oidc.metadata.update-enabled", havingValue = "true", matchIfMissing = true)
public class OIDCProviderMetadataSchedulingConfiguration {

    @Autowired
    private OIDCProviderMetadataService oidcProviderMetadataService;

    @Scheduled(cron = "${eidas.proxy.oidc.metadata.update-schedule:0 0 0/24 * * ?}")
    public void updateMetadata() {
        oidcProviderMetadataService.updateMetadata();
    }
}

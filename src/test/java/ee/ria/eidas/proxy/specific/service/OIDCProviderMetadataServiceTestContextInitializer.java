package ee.ria.eidas.proxy.specific.service;

import jakarta.annotation.Nonnull;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class OIDCProviderMetadataServiceTestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(@Nonnull ConfigurableApplicationContext configurableApplicationContext) {
        String currentDirectory = System.getProperty("user.dir");
        System.setProperty("SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/mock_eidasnode");
        System.setProperty("EIDAS_PROXY_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/mock_eidasnode");
    }
}

package ee.ria.eidas.proxy.specific.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

// TODO eidas-attributes should be included in war with sensible defaults allowing overriding when necessary
// TODO test the configuration - to see if the cache has been defined in configuration file?

@ConfigurationProperties(prefix = "eidas.proxy")
@Validated
@Data
@Slf4j
public class SpecificProxyServiceProperties {

    @Valid
    private OidcProviderProperties oidc = new OidcProviderProperties();

    @ConfigurationProperties(prefix = "oidc")
    @Data
    public static class OidcProviderProperties {

        private String scope = "openid idcard mid";

        @NotNull
        private String clientId;

        @NotNull
        private String clientSecret;

        @NotNull
        private String redirectUri;

        @NotNull
        private String issuerUrl;
    }
}

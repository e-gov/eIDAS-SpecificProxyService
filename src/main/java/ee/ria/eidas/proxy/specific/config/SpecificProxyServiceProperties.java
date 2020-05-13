package ee.ria.eidas.proxy.specific.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

// TODO eidas-attributes should be included in war with sensible defaults allowing overriding when necessary
// TODO test the configuration - to see if the cache has been defined in configuration file?

@ConfigurationProperties(prefix = "eidas.proxy")
@Validated
@Data
@Slf4j
public class SpecificProxyServiceProperties {

    @PostConstruct
    public void init() {
        if (askConsent) {
            Assert.notNull(consentBinaryLightToken.issuer, "eidas.proxy.consent-binary-light-token.issuer cannot be null when eidas.proxy.ask-consent is 'true'");
            Assert.notNull(consentBinaryLightToken.algorithm, "eidas.proxy.consent-binary-light-token.algorithm cannot be null when eidas.proxy.ask-consent is 'true'");
            Assert.notNull(consentBinaryLightToken.secret, "eidas.proxy.consent-binary-light-token.secret cannot be null when eidas.proxy.ask-consent is 'true'");
        }
    }

    boolean askConsent = true;

    @NotNull
    private String nodeSpecificResponseUrl;

    @Valid
    private ConsentProperties consentBinaryLightToken = new ConsentProperties();

    @Valid
    private OidcProviderProperties oidc = new OidcProviderProperties();

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

    @Data
    public static class ConsentProperties {

        private String issuer;

        private String secret;

        private String algorithm;
    }
}

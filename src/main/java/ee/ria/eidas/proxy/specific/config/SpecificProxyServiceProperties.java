package ee.ria.eidas.proxy.specific.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

// TODO eidas-attributes should be included in war with sensible defaults allowing overriding when necessary
// TODO enums

@ConfigurationProperties(prefix = "eidas.proxy")
@Validated
@Data
@Slf4j
public class SpecificProxyServiceProperties {

    @PostConstruct
    public void init() {
        if (askConsent) {
            assertConsentCommunicationDefinitionsPresent();
        }
        // TODO assert oidc scope map
        // TODO oidc scope mappings from separate config file (embedded into war)
        // TODO verify that claim mappings exist for mandatory attribute set
    }

    private void assertConsentCommunicationDefinitionsPresent() {
        Assert.notNull(consentBinaryLightToken.issuer, "eidas.proxy.consent-binary-light-token.issuer cannot be null when eidas.proxy.ask-consent is 'true'");
        Assert.notNull(consentBinaryLightToken.algorithm, "eidas.proxy.consent-binary-light-token.algorithm cannot be null when eidas.proxy.ask-consent is 'true'");
        Assert.notNull(consentBinaryLightToken.secret, "eidas.proxy.consent-binary-light-token.secret cannot be null when eidas.proxy.ask-consent is 'true'");
    }

    private boolean askConsent = true;

    private List<String> supportedSpTypes = Arrays.asList("public", "private");

    @NotNull
    private String nodeSpecificResponseUrl;

    @Valid
    private ConsentProperties consentBinaryLightToken = new ConsentProperties();

    @Valid
    private OidcProviderProperties oidc = new OidcProviderProperties();

    @Valid
    private WebappProperties webapp = new WebappProperties();

    @Valid
    @Data
    public static class WebappProperties {

        private List<HttpMethod> disabledHttpMethods = Arrays.asList(GET, POST);
    }

    @Data
    @NoArgsConstructor
    public static class OidcProviderProperties {

        private List<String> scope = asList("idcard", "mid");

        @NotNull
        private String clientId;

        @NotNull
        private String clientSecret;

        @NotNull
        private String redirectUri;

        @NotNull
        private String issuerUrl;

        private String defaultUiLanguage = "et";

        private Integer maxClockSkewInSeconds = 30;

        private Integer readTimeoutInMilliseconds = 5000;

        private Integer connectTimeoutInMilliseconds = 5000;

        private Map<String, String> attributeScopeMapping = new HashMap<>();

        private SpecificProxyServiceProperties.IdTokenClaimMappingProperties responseClaimMapping = new SpecificProxyServiceProperties.IdTokenClaimMappingProperties();

        private String errorCodeUserCancel = "user_cancel";

    }

    @Data
    public static class ConsentProperties {

        private String issuer;

        private String secret;

        private String algorithm;
    }

    @Data
    public static class IdTokenClaimMappingProperties {

        private String id = "$.jti";

        private String issuer = "$.iss";

        private String acr = "$.acr";

        private Map<String, String> attributes = new HashMap<>();
    }
}

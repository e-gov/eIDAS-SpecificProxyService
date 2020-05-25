package ee.ria.eidas.proxy.specific.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.springframework.http.HttpMethod.*;

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
    @AllArgsConstructor
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

        private Map<String, String> attributeScopeMapping = new HashMap<>();

        private String errorCodeUserCancel = "user_cancel";

        private String errorCodeAccessDenied = "access_denied";
    }

    @Data
    public static class ConsentProperties {

        private String issuer;

        private String secret;

        private String algorithm;
    }
}

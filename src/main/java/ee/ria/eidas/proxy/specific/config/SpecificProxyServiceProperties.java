package ee.ria.eidas.proxy.specific.config;

import eu.eidas.auth.commons.protocol.eidas.spec.EidasSpec;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@ConfigurationProperties(prefix = "eidas.proxy")
@Validated
@Data
@Slf4j
public class SpecificProxyServiceProperties {

    public static final List<String> NATURAL_PERSON_MANDATORY_ATTRIBUTE_SET = Collections.unmodifiableList(asList(
            EidasSpec.Definitions.PERSON_IDENTIFIER.getFriendlyName(),
            EidasSpec.Definitions.DATE_OF_BIRTH.getFriendlyName(),
            EidasSpec.Definitions.CURRENT_FAMILY_NAME.getFriendlyName(),
            EidasSpec.Definitions.CURRENT_GIVEN_NAME.getFriendlyName()));

    @PostConstruct
    public void init() {
        if (askConsent) {
            assertConsentCommunicationDefinitionsPresent();
        }

        assertScopeMappingsIfPresent();
        assertOidcClaimMappingsConfigurationPresent();
        assertOidcClaimMappingPostProcessingRules();
    }

    private boolean askConsent = true;

    private List<String> supportedSpTypes = asList("public", "private");

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

        private List<HttpMethod> disabledHttpMethods = asList(GET, POST);
    }

    @Data
    @NoArgsConstructor
    public static class OidcProviderProperties {

        private List<String> scope = asList("idcard", "mid");

        private List<String> acceptedAmrValues = asList("idcard", "mID");

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

        private String subject = "$.sub";

        private String id = "$.jti";

        private String issuer = "$.iss";

        private String acr = "$.acr";

        private Map<String, String> attributes = new HashMap<>();

        private Map<String, String> attributesPostProcessing = new HashMap<>();
    }


    private void assertOidcClaimMappingPostProcessingRules() {
        if(!oidc.getResponseClaimMapping().getAttributesPostProcessing().isEmpty()) {
            List<String> invalidRegexValues = oidc.getResponseClaimMapping().getAttributesPostProcessing().values().stream().filter(item -> {
                try {
                    Pattern.compile(item);
                    return false;
                } catch (PatternSyntaxException e) {
                    return true;
                }
            }).collect(Collectors.toList());
            Assert.isTrue(invalidRegexValues.isEmpty(), "Invalid claim post processing rules detected. The following configuration values are not valid regex expressions: " + invalidRegexValues + ". Please check your configuration");

            List<String> invalidParameterValues = oidc.getResponseClaimMapping().getAttributesPostProcessing().values().stream().filter(item -> !item.contains("(?<attributeValue>")).collect(Collectors.toList());
            Assert.isTrue(invalidParameterValues.isEmpty(), "Invalid claim post processing rules detected: " + invalidParameterValues + ". A named regex group must specified to extract the claim value. Please check your configuration");
        }
    }

    private void assertScopeMappingsIfPresent() {
        if (!oidc.getAttributeScopeMapping().isEmpty()) {
            List<String> missingMandatoryParameters = NATURAL_PERSON_MANDATORY_ATTRIBUTE_SET.stream()
                    .distinct().filter(item -> !oidc.getAttributeScopeMapping().keySet().contains(item))
                    .collect(Collectors.toList());
            Assert.isTrue(missingMandatoryParameters.isEmpty(), "Missing scope mapping for the following mandatory attributes: " + missingMandatoryParameters + ". Please check your configuration");
        }
    }

    private void assertOidcClaimMappingsConfigurationPresent() {
        Assert.isTrue(!oidc.getResponseClaimMapping().getAttributes().isEmpty(), "Missing claim mappings configuration!");
        List<String> missingMandatoryParameters = NATURAL_PERSON_MANDATORY_ATTRIBUTE_SET.stream()
                .distinct().filter(item -> !oidc.getResponseClaimMapping().getAttributes().keySet().contains(item))
                .collect(Collectors.toList());
        Assert.isTrue(missingMandatoryParameters.isEmpty(), "Missing claim mapping for the following mandatory attributes: " + missingMandatoryParameters + ". Please check your configuration");
    }

    private void assertConsentCommunicationDefinitionsPresent() {
        Assert.notNull(consentBinaryLightToken.issuer, "eidas.proxy.consent-binary-light-token.issuer cannot be null when eidas.proxy.ask-consent is 'true'");
        Assert.notNull(consentBinaryLightToken.algorithm, "eidas.proxy.consent-binary-light-token.algorithm cannot be null when eidas.proxy.ask-consent is 'true'");
        Assert.notNull(consentBinaryLightToken.secret, "eidas.proxy.consent-binary-light-token.secret cannot be null when eidas.proxy.ask-consent is 'true'");
    }
}

package ee.ria.eidas.proxy.specific.config;

import eu.eidas.auth.commons.protocol.eidas.spec.EidasSpec;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
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

        log.info("Configuration: {}", toString());
    }

    private boolean askConsent = true;

    private List<String> supportedSpTypes = asList("public");

    @NotNull
    private String appInstanceId;

    @NotNull
    private String nodeSpecificResponseUrl;

    @Valid
    private ConsentProperties consentBinaryLightToken = new ConsentProperties();

    @Valid
    private OidcProviderProperties oidc = new OidcProviderProperties();

    @Valid
    private CacheProperties communicationCache = new CacheProperties();

    @Valid
    private WebappProperties webapp = new WebappProperties();

    @Valid
    @ToString
    @Data
    public static class WebappProperties {
        public static final String DEFAULT_CONTENT_SECURITY_POLICY = "block-all-mixed-content; default-src 'self'; object-src: 'none'; frame-ancestors 'none';";

        private List<HttpMethod> allowedHttpMethods = asList(GET, POST);

        private String sessionIdCookieName = "JSESSIONID";

        @NotEmpty
        private String contentSecurityPolicy = DEFAULT_CONTENT_SECURITY_POLICY;
    }

    @Data
    @ToString
    @NoArgsConstructor
    public static class OidcProviderProperties {

        private List<String> scope = asList("idcard", "mid");

        private List<String> acceptedAmrValues = asList("idcard", "mID");

        @NotNull
        private String clientId;

        @NotNull
        @ToString.Exclude
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
    @ToString
    public static class ConsentProperties {

        private String issuer;

        @ToString.Exclude
        private String secret;

        private String algorithm;
    }

    @Data
    @ToString
    public static class IdTokenClaimMappingProperties {

        private String naturalPersonSubject = "$.sub";

        private String legalPersonSubject = "$.profile_attributes.represents_legal_person.registry_code";

        private String id = "$.jti";

        private String issuer = "$.iss";

        private String acr = "$.acr";

        private Map<String, String> attributes = new HashMap<>();

        private Map<String, String> attributesPostProcessing = new HashMap<>();
    }

    @Data
    @ToString
    public static class CacheProperties {

        public static final String INCOMING_NODE_REQUESTS_CACHE = "incoming-node-requests-cache";
        public static final String OUTGOING_NODE_RESPONSES_CACHE = "outgoing-node-responses-cache";
        public static final String IDP_PENDING_REQUESTS_CACHE = "pending-idp-requests-cache";
        public static final String IDP_PENDING_CONSENT_MAP = "pending-user-consents-cache";

        private Map<String, String> cacheNameMapping = Stream.of(
                new AbstractMap.SimpleEntry<>(INCOMING_NODE_REQUESTS_CACHE, "nodeSpecificProxyserviceRequestCache"),
                new AbstractMap.SimpleEntry<>(OUTGOING_NODE_RESPONSES_CACHE, "specificNodeProxyserviceResponseCache"),
                new AbstractMap.SimpleEntry<>(IDP_PENDING_REQUESTS_CACHE, "specificMSIdpRequestCorrelationMap"),
                new AbstractMap.SimpleEntry<>(IDP_PENDING_CONSENT_MAP, "specificMSIdpConsentCorrelationMap"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        @NotNull
        private String igniteConfigurationFileLocation;

        private String igniteConfigurationBeanName = "igniteSpecificCommunication.cfg";
    }


    private void assertOidcClaimMappingPostProcessingRules() {
        if (!oidc.getResponseClaimMapping().getAttributesPostProcessing().isEmpty()) {
            List<String> invalidRegexValues = oidc.getResponseClaimMapping().getAttributesPostProcessing().values().stream().filter(item -> {
                try {
                    Pattern.compile(item);
                    return false;
                } catch (PatternSyntaxException e) {
                    return true;
                }
            }).collect(Collectors.toList());
            Assert.isTrue(invalidRegexValues.isEmpty(), format("Invalid claim post processing rules detected. The following configuration values are not valid regex expressions: %s. Please check your configuration", invalidRegexValues));

            List<String> invalidParameterValues = oidc.getResponseClaimMapping().getAttributesPostProcessing().values().stream().filter(item -> !item.contains("(?<attributeValue>")).collect(Collectors.toList());
            Assert.isTrue(invalidParameterValues.isEmpty(), format("Invalid claim post processing rules detected: %s. A named regex group must specified to extract the claim value. Please check your configuration", invalidParameterValues));
        }
    }

    private void assertScopeMappingsIfPresent() {
        if (!oidc.getAttributeScopeMapping().isEmpty()) {
            List<String> missingMandatoryParameters = NATURAL_PERSON_MANDATORY_ATTRIBUTE_SET.stream()
                    .distinct().filter(item -> !oidc.getAttributeScopeMapping().containsKey(item))
                    .collect(Collectors.toList());
            Assert.isTrue(missingMandatoryParameters.isEmpty(), format("Missing scope mapping for the following mandatory attributes: %s. Please check your configuration", missingMandatoryParameters));
        }
    }

    private void assertOidcClaimMappingsConfigurationPresent() {
        Assert.isTrue(!oidc.getResponseClaimMapping().getAttributes().isEmpty(), "Missing claim mappings configuration!");
        List<String> missingMandatoryParameters = NATURAL_PERSON_MANDATORY_ATTRIBUTE_SET.stream()
                .distinct().filter(item -> !oidc.getResponseClaimMapping().getAttributes().containsKey(item))
                .collect(Collectors.toList());
        Assert.isTrue(missingMandatoryParameters.isEmpty(), "Missing claim mapping for the following mandatory attributes: " + missingMandatoryParameters + ". Please check your configuration");
    }

    private void assertConsentCommunicationDefinitionsPresent() {
        Assert.notNull(consentBinaryLightToken.issuer, "eidas.proxy.consent-binary-light-token.issuer cannot be null when eidas.proxy.ask-consent is 'true'");
        Assert.notNull(consentBinaryLightToken.algorithm, "eidas.proxy.consent-binary-light-token.algorithm cannot be null when eidas.proxy.ask-consent is 'true'");
        Assert.notNull(consentBinaryLightToken.secret, "eidas.proxy.consent-binary-light-token.secret cannot be null when eidas.proxy.ask-consent is 'true'");
    }
}

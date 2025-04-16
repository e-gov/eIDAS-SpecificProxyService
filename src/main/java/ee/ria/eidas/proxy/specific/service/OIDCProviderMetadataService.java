package ee.ria.eidas.proxy.specific.service;

import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties.OidcProviderProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import static com.nimbusds.jose.JWSAlgorithm.RS256;

@Slf4j
@Service
@RequiredArgsConstructor
public class OIDCProviderMetadataService {
    private static final String HTTPS_PROTOCOL = "https";
    private final AtomicReference<OIDCProviderMetadata> oidcProviderMetadata = new AtomicReference<>();
    private final AtomicReference<IDTokenValidator> oidcIDTokenValidator = new AtomicReference<>();
    private final SpecificProxyServiceProperties specificProxyServiceProperties;

    public OIDCProviderMetadata getOidcProviderMetadata() {
        return oidcProviderMetadata.get();
    }

    public IDTokenValidator getIdTokenValidator() {
        return oidcIDTokenValidator.get();
    }

    @PostConstruct
    @Scheduled(cron = "${eidas.proxy.oidc.metadata.update-schedule:0 0 0/24 * * ?}")
    @Retryable(value = {IllegalStateException.class}, maxAttemptsExpression = "${eidas.proxy.oidc.metadata.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${eidas.proxy.oidc.metadata.backoff-delay-in-milliseconds:60000}"))
    public void updateMetadata() throws RuntimeException {
        log.info("Updating OIDC metadata for issuer: {}", specificProxyServiceProperties.getOidc().getIssuerUrl());
        oidcProviderMetadata.set(requestOidcProviderMetadata());
        oidcIDTokenValidator.set(createIdTokenValidator());
    }

    private OIDCProviderMetadata requestOidcProviderMetadata() {
        OidcProviderProperties oidcProperties = specificProxyServiceProperties.getOidc();
        String issuerUrl = oidcProperties.getIssuerUrl();
        try {
            Issuer issuer = new Issuer(issuerUrl);
            OIDCProviderConfigurationRequest request = new OIDCProviderConfigurationRequest(issuer);
            if (!HTTPS_PROTOCOL.equals(request.getEndpointURI().toURL().getProtocol())) {
                log.warn("OpenID Connect provider metadata issuer URL is not using HTTPS protocol: {}", issuerUrl);
            }
            HTTPRequest httpRequest = request.toHTTPRequest();
            httpRequest.setConnectTimeout(oidcProperties.getConnectTimeoutInMilliseconds());
            httpRequest.setReadTimeout(oidcProperties.getReadTimeoutInMilliseconds());
            HTTPResponse httpResponse = httpRequest.send();
            if (!httpResponse.indicatesSuccess()) {
                throw new IllegalStateException("Failed to fetch OpenID Connect provider metadata from issuer: "
                        + issuerUrl + ", Invalid response status: " + httpResponse.getStatusCode());
            }
            OIDCProviderMetadata oidcProviderMetadata = OIDCProviderMetadata.parse(httpResponse.getBodyAsJSONObject());
            log.info("Successfully updated OIDC metadata for issuer: {}", issuerUrl);
            return oidcProviderMetadata;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch OpenID Connect provider metadata from issuer: " + issuerUrl, e);
        }
    }

    private IDTokenValidator createIdTokenValidator() {
        try {
            OIDCProviderMetadata oidcProviderMetadata = getOidcProviderMetadata();
            Issuer iss = new Issuer(oidcProviderMetadata.getIssuer());
            ClientID clientID = new ClientID(specificProxyServiceProperties.getOidc().getClientId());
            URL jwkSetURL = oidcProviderMetadata.getJWKSetURI().toURL();
            if (!HTTPS_PROTOCOL.equals(jwkSetURL.getProtocol())) {
                log.warn("JWKS URL returned by OpenID Connect provider metadata is not using HTTPS protocol: {}",
                        jwkSetURL);
            }
            IDTokenValidator validator = new IDTokenValidator(iss, clientID, RS256, jwkSetURL);
            validator.setMaxClockSkew(specificProxyServiceProperties.getOidc().getMaxClockSkewInSeconds());
            log.info("Successfully updated OIDC token validator for issuer: {}", specificProxyServiceProperties.getOidc().getIssuerUrl());
            return validator;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to setup OpenID Connect token validator for issuer: " + specificProxyServiceProperties.getOidc().getIssuerUrl(), e);
        }
    }

    @Recover
    public void logUnsuccessfulUpdate(IllegalStateException e) {
        log.error("Unable to update OIDC metadata", e);
        throw e;
    }
}

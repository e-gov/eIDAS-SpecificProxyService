package ee.ria.eidas.proxy.specific.storage;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.error.BadRequestException;
import eu.eidas.auth.commons.exceptions.SecurityEIDASException;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.cache.Cache;
import java.io.Serializable;
import java.net.URL;
import java.util.Map;

import static ee.ria.eidas.proxy.specific.config.LogFieldNames.*;
import static net.logstash.logback.argument.StructuredArguments.value;
import static net.logstash.logback.marker.Markers.append;

@Slf4j
@Service
public class SpecificProxyServiceCommunication {

    @Autowired
    private SpecificProxyServiceProperties specificProxyServiceProperties;

    @Lazy
    @Autowired
    private Cache<String, CorrelatedRequestsHolder> idpRequestCommunicationCache;

    @Lazy
    @Autowired
    private Cache<String, ILightResponse> idpConsentCommunicationCache;

    public BinaryLightToken putPendingLightResponse(ILightResponse lightResponse) throws SpecificCommunicationException {
        final BinaryLightToken binaryLightToken = BinaryLightTokenHelper.createBinaryLightToken(
                specificProxyServiceProperties.getConsentBinaryLightToken().getIssuer(),
                specificProxyServiceProperties.getConsentBinaryLightToken().getSecret(),
                specificProxyServiceProperties.getConsentBinaryLightToken().getAlgorithm());

        boolean isInserted = idpConsentCommunicationCache.putIfAbsent(binaryLightToken.getToken().getId(), lightResponse);

        if (isInserted) {
            if (log.isInfoEnabled())
                log.info(append(LIGHT_RESPONSE, lightResponse)
                                .and(append(IGNITE_CACHE_NAME, idpConsentCommunicationCache.getName())),
                        "LightResponse was saved with tokenId: '{}' ",
                        value(LIGHT_RESPONSE_LIGHT_TOKEN_ID, binaryLightToken.getToken().getId()));
        } else {
            if (log.isErrorEnabled())
                log.error(append(LIGHT_RESPONSE, lightResponse)
                                .and(append(IGNITE_CACHE_NAME, idpConsentCommunicationCache.getName())),
                        "LightResponse with tokenId: '{}' already exists",
                        value(LIGHT_RESPONSE_LIGHT_TOKEN_ID, binaryLightToken.getToken().getId()));
        }

        return binaryLightToken;
    }

    public ILightResponse getAndRemovePendingLightResponse(String binaryLightTokenBase64) {
        Assert.isTrue(StringUtils.isNotEmpty(binaryLightTokenBase64), "Token value cannot be null or empty!");

        try {
            final String lightTokenId = BinaryLightTokenHelper.getBinaryLightTokenId(binaryLightTokenBase64,
                    specificProxyServiceProperties.getConsentBinaryLightToken().getSecret(),
                    specificProxyServiceProperties.getConsentBinaryLightToken().getAlgorithm());

            ILightResponse lightResponse = idpConsentCommunicationCache.getAndRemove(lightTokenId);

            if (lightResponse != null) {

                if (log.isInfoEnabled())
                    log.info(append(LIGHT_RESPONSE, lightResponse)
                                    .and(append(IGNITE_CACHE_NAME, idpConsentCommunicationCache.getName())),
                            "LightResponse retrieved from cache for tokenId: '{}'",
                            value(LIGHT_RESPONSE_LIGHT_TOKEN_ID, lightTokenId));
            } else {

                if (log.isWarnEnabled())
                    log.warn(append(IGNITE_CACHE_NAME, idpConsentCommunicationCache.getName()),
                            "LightResponse not found from cache for tokenId: '{}'",
                            value(LIGHT_RESPONSE_LIGHT_TOKEN_ID, lightTokenId));
            }

            return lightResponse;

        } catch (SpecificCommunicationException | SecurityEIDASException e) {
            throw new BadRequestException("Invalid token", e);
        }
    }

    public void putIdpRequest(String state, CorrelatedRequestsHolder requestsHolder) {
        boolean isInserted = idpRequestCommunicationCache.putIfAbsent(state, requestsHolder);

        if (isInserted) {

            if (log.isInfoEnabled())
                log.info(append(IDP_REQUEST_CORRELATED_REQUESTS, requestsHolder)
                    .and(append(IGNITE_CACHE_NAME, idpRequestCommunicationCache.getName())),
                        "Pending IDP request was saved with tokenId: '{}' ",
                        value(IDP_REQUEST_LIGHT_TOKEN_ID, state));
        } else {

            if (log.isErrorEnabled())
                log.error(append(IDP_REQUEST_CORRELATED_REQUESTS, requestsHolder)
                            .and(append(IGNITE_CACHE_NAME, idpRequestCommunicationCache.getName())),
                        "Pending IDP request already exists with tokenId: '{}' ",
                    value(IDP_REQUEST_LIGHT_TOKEN_ID, state));
        }
    }

    public ILightRequest getAndRemoveIdpRequest(String inResponseToId) {
        CorrelatedRequestsHolder correlatedRequestsHolder = idpRequestCommunicationCache.getAndRemove(inResponseToId);

        if (correlatedRequestsHolder != null) {
            ILightRequest originalLightRequest = correlatedRequestsHolder.getLightRequest();

            if (log.isInfoEnabled())
                log.info(append(IDP_REQUEST_CORRELATED_REQUESTS, correlatedRequestsHolder)
                                .and(append(IGNITE_CACHE_NAME, idpRequestCommunicationCache.getName())),
                        "Pending IDP request retrieved from cache for id: '{}'",
                        value(IDP_REQUEST_LIGHT_TOKEN_ID, inResponseToId));
            return originalLightRequest;
        } else {

            if (log.isWarnEnabled())
                log.warn(append(IGNITE_CACHE_NAME, idpRequestCommunicationCache.getName()),
                        "Pending IDP request not found from cache for id: '{}'",
                        value(IDP_REQUEST_LIGHT_TOKEN_ID, inResponseToId));

            return null;
        }
    }

    /**
     * Holds the light request and the correlated specific IDP request.
     */
    public static class CorrelatedRequestsHolder implements Serializable {

        private static final long serialVersionUID = 8942548697342198159L;

        @Getter
        private final ILightRequest lightRequest;

        @Getter
        private final Map<String, URL> authenticationRequest;

        public CorrelatedRequestsHolder(ILightRequest lightRequest, Map<String, URL> authenticationRequest) {
            Assert.notNull(lightRequest, "Original LightRequest missing!");
            Assert.notNull(authenticationRequest, "IDP authentication request missing!");
            this.lightRequest = lightRequest;
            this.authenticationRequest = authenticationRequest;
        }

        public String getIdpAuthenticationRequestState() {
            return authenticationRequest.entrySet().stream().findFirst().orElseThrow(() -> new IllegalStateException("Missing IDP authentication state")).getKey();
        }

        public URL getIdpAuthenticationRequest() {
            return authenticationRequest.entrySet().stream().findFirst().orElseThrow(() -> new IllegalStateException("Missing IDP authentication request")).getValue();
        }
    }
}

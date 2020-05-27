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
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.cache.Cache;
import java.io.Serializable;
import java.net.URL;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class SpecificProxyServiceCommunication {

    @Autowired
    private SpecificProxyServiceProperties specificProxyServiceProperties;

    @Autowired
    private Cache<String, CorrelatedRequestsHolder> idpRequestCommunicationCache;

    @Autowired
    private Cache<String, ILightResponse> idpConsentCommunicationCache;

    public BinaryLightToken putPendingLightResponse(ILightResponse lightResponse) throws SpecificCommunicationException {
        final BinaryLightToken binaryLightToken = BinaryLightTokenHelper.createBinaryLightToken(
                specificProxyServiceProperties.getConsentBinaryLightToken().getIssuer(),
                specificProxyServiceProperties.getConsentBinaryLightToken().getSecret(),
                specificProxyServiceProperties.getConsentBinaryLightToken().getAlgorithm());

        idpConsentCommunicationCache.put(binaryLightToken.getToken().getId(), lightResponse);
        return binaryLightToken;
    }

    public ILightResponse getAndRemovePendingLightResponse(String binaryLightTokenBase64) throws SpecificCommunicationException {
        if (StringUtils.isNotEmpty(binaryLightTokenBase64)) {

            try {

                final String lightTokenId = BinaryLightTokenHelper.getBinaryLightTokenId(binaryLightTokenBase64,
                        specificProxyServiceProperties.getConsentBinaryLightToken().getSecret(),
                        specificProxyServiceProperties.getConsentBinaryLightToken().getAlgorithm());

                final ILightResponse iLightResponse = idpConsentCommunicationCache.get(lightTokenId);
                if (null != iLightResponse) {
                    idpConsentCommunicationCache.remove(lightTokenId);
                }

                return iLightResponse;

            } catch (SpecificCommunicationException | SecurityEIDASException e) {
                throw new BadRequestException("Invalid token", e);
            }

        } else {
            return null;
        }
    }

    public void putIdpRequest(String state, CorrelatedRequestsHolder requestsHolder) throws SpecificCommunicationException {
        boolean isInserted = idpRequestCommunicationCache.putIfAbsent(state, requestsHolder);
        if (isInserted) {
            log.info("IDP request with ID: '{}' was saved. Cache: '{}'. IDP request: '{}'", state, idpRequestCommunicationCache.getName(), requestsHolder.getAuthenticationRequest().values());
        } else {
            log.error("IDP request not stored! Value already exists for key: '{}'! Cache: '{}'. IDP request: '{}'", state, idpRequestCommunicationCache.getName(), requestsHolder.getAuthenticationRequest().values());
        }
    }

    public ILightRequest getAndRemoveIdpRequest(String inResponseToId) throws SpecificCommunicationException {
        Optional<ILightRequest> originalLightRequest = Optional.ofNullable(idpRequestCommunicationCache.get(inResponseToId))
                .map( CorrelatedRequestsHolder::getILightRequest);
        if (originalLightRequest.isPresent()) {
            log.debug("Found and removed IDP request for id: '{}'", inResponseToId);
            idpRequestCommunicationCache.remove(inResponseToId);
            return originalLightRequest.get();
        } else {
            log.warn("Failed to find the IDP request for id: '{}' ", inResponseToId);
            return null;
        }
    }

    /**
     * Holds the light request and the correlated specific request.
     */
    public static class CorrelatedRequestsHolder implements Serializable {

        private static final long serialVersionUID = 8942548697342198159L;

        @Getter
        private final ILightRequest iLightRequest;

        @Getter
        private final Map<String, URL> authenticationRequest;

        public CorrelatedRequestsHolder(ILightRequest iLightRequest, Map<String, URL> authenticationRequest) {
            Assert.notNull(iLightRequest, "Original LightRequest missing!");
            Assert.notNull(authenticationRequest, "IDP authentication request missing!");
            this.iLightRequest = iLightRequest;
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

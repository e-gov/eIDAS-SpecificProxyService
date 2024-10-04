package ee.ria.eidas.proxy.specific.storage;

import ee.ria.eidas.proxy.specific.error.BadRequestException;
import ee.ria.eidas.proxy.specific.error.RequestDeniedException;
import eu.eidas.auth.commons.EIDASStatusCode;
import eu.eidas.auth.commons.EIDASSubStatusCode;
import eu.eidas.auth.commons.attribute.AttributeRegistry;
import eu.eidas.auth.commons.exceptions.SecurityEIDASException;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.light.impl.LightResponse;
import eu.eidas.auth.commons.light.impl.ResponseStatus;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.cache.Cache;
import java.util.UUID;

import static ee.ria.eidas.proxy.specific.config.LogFieldNames.*;
import static net.logstash.logback.argument.StructuredArguments.value;
import static net.logstash.logback.marker.Markers.append;

@Slf4j
@Service
public class EidasNodeCommunication {
    private static final LightJAXBCodec codec = LightJAXBCodec.buildDefault();

    @Value("${lightToken.proxyservice.request.issuer.name}")
    private String lightTokenRequestIssuerName;

    @Value("${lightToken.proxyservice.request.secret}")
    private String lightTokenRequestSecret;

    @Value("${lightToken.proxyservice.request.algorithm}")
    private String lightTokenRequestAlgorithm;

    @Getter
    @Value("${lightToken.proxyservice.response.issuer.name}")
    private String lightTokenResponseIssuerName;

    @Value("${lightToken.proxyservice.response.secret}")
    private String lightTokenResponseSecret;

    @Value("${lightToken.proxyservice.response.algorithm}")
    private String lightTokenResponseAlgorithm;

    @Lazy
    @Autowired
    @Qualifier("nodeSpecificProxyserviceRequestCache")
    private Cache<String, String> eidasRequestCommunicationCache;

    @Lazy
    @Autowired
    @Qualifier("nodeSpecificProxyserviceResponseCache")
    private Cache<String, String> eidasResponseCommunicationCache;

    @Autowired
    private AttributeRegistry eidasAttributeRegistry;

    private static ILightResponse createILightResponseFailure(String inResponseTo, String statusMessage, String issuer) {
        final ResponseStatus responseStatus = ResponseStatus.builder()
                .statusCode(EIDASStatusCode.REQUESTER_URI.toString())
                .subStatusCode(EIDASSubStatusCode.REQUEST_DENIED_URI.toString())
                .statusMessage(statusMessage)
                .failure(true)
                .build();

        return new LightResponse.Builder()
                .id(UUID.randomUUID().toString())
                .inResponseToId(inResponseTo)
                .issuer(issuer)
                .status(responseStatus).build();
    }

    @PostConstruct
    public void init() {
        Assert.notNull(lightTokenRequestIssuerName, "lightToken.proxyservice.request.issuer.name cannot be null. Please check your configuration");
        Assert.notNull(lightTokenRequestSecret, "lightToken.proxyservice.request.secret cannot be null. Please check your configuration");
        Assert.notNull(lightTokenRequestAlgorithm, "lightToken.proxyservice.request.algorithm cannot be null. Please check your configuration");
        Assert.notNull(lightTokenResponseIssuerName, "lightToken.proxyservice.response.issuer.name cannot be null. Please check your configuration");
        Assert.notNull(lightTokenResponseSecret, "lightToken.proxyservice.response.secret cannot be null. Please check your configuration");
        Assert.notNull(lightTokenResponseAlgorithm, "lightToken.proxyservice.response.algorithm cannot be null. Please check your configuration");
    }

    public BinaryLightToken putResponse(final ILightResponse lightResponse) throws SpecificCommunicationException {
        final BinaryLightToken binaryLightToken = BinaryLightTokenHelper.createBinaryLightToken(
                lightTokenResponseIssuerName, lightTokenResponseSecret, lightTokenResponseAlgorithm);
        final String tokenId = binaryLightToken.getToken().getId();
        boolean isInserted = eidasResponseCommunicationCache.putIfAbsent(tokenId, codec.marshall(lightResponse));

        if (isInserted && log.isInfoEnabled()) {
            log.info(append(LIGHT_RESPONSE, lightResponse)
                            .and(append(IGNITE_CACHE_NAME, eidasRequestCommunicationCache.getName())), "LightResponse with tokenId: '{}' was saved",
                    value(LIGHT_RESPONSE_LIGHT_TOKEN_ID, tokenId));
        } else if (log.isWarnEnabled()) {
            log.warn(append(LIGHT_RESPONSE, lightResponse).and(append(IGNITE_CACHE_NAME, eidasRequestCommunicationCache.getName())),
                    "LightResponse was not saved. A LightResponse with tokenId: '{}' already exists",
                    value(LIGHT_RESPONSE_LIGHT_TOKEN_ID, tokenId));
        }
        return binaryLightToken;
    }

    public BinaryLightToken putErrorResponse(RequestDeniedException ex) throws SpecificCommunicationException {
        ILightResponse lightResponse = createILightResponseFailure(ex.getInResponseTo(),
                ex.getMessage(),
                getLightTokenResponseIssuerName());
        return putResponse(lightResponse);
    }

    public ILightRequest getAndRemoveRequest(final String tokenBase64) throws SpecificCommunicationException {
        Assert.isTrue(StringUtils.isNotEmpty(tokenBase64), "Token value cannot be null or empty!");
        final String tokenId = getBinaryLightTokenId(tokenBase64);
        String lightRequest = eidasRequestCommunicationCache.getAndRemove(tokenId);
        ILightRequest request = codec.unmarshallRequest(lightRequest, eidasAttributeRegistry.getAttributes());

        if (request != null) {
            log.info(append(LIGHT_REQUEST_CITIZEN_COUNTRY_CODE, request.getCitizenCountryCode()).and(append(IGNITE_CACHE_NAME, eidasRequestCommunicationCache.getName())),
                    "LightRequest retrieved from cache for tokenId: {}", value(LIGHT_REQUEST_LIGHT_TOKEN_ID, tokenId));
        } else if (log.isWarnEnabled()) {
            log.warn(append(IGNITE_CACHE_NAME, eidasRequestCommunicationCache.getName()),
                    "LightRequest was not found from cache for tokenId: {}", value(LIGHT_REQUEST_LIGHT_TOKEN_ID, tokenId));
        }
        return request;

    }

    private String getBinaryLightTokenId(String tokenBase64) {
        try {
            return BinaryLightTokenHelper.getBinaryLightTokenId(tokenBase64, lightTokenRequestSecret, lightTokenRequestAlgorithm);
        } catch (SpecificCommunicationException | SecurityEIDASException e) {
            throw new BadRequestException("Invalid token", e);
        }
    }
}

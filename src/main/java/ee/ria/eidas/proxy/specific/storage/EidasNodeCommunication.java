package ee.ria.eidas.proxy.specific.storage;

import ee.ria.eidas.proxy.specific.error.BadRequestException;
import ee.ria.eidas.proxy.specific.error.RequestDeniedException;
import eu.eidas.auth.commons.EIDASStatusCode;
import eu.eidas.auth.commons.EIDASSubStatusCode;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.AttributeRegistry;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.exceptions.SecurityEIDASException;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.light.impl.LightRequest;
import eu.eidas.auth.commons.light.impl.LightResponse;
import eu.eidas.auth.commons.light.impl.ResponseStatus;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.cache.Cache;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.util.Collection;
import java.util.UUID;

@Slf4j
@Service
public class EidasNodeCommunication {

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

    @Autowired
    @Qualifier("nodeSpecificProxyserviceRequestCache")
    private Cache<String, String> eidasRequestCommunicationCache;

    @Autowired
    @Qualifier("nodeSpecificProxyserviceResponseCache")
    private Cache<String, String> eidasResponseCommunicationCache;

    @Autowired
    private AttributeRegistry eidasAttributeRegistry;

    private static LightJAXBCodec codec;

    static {
        try {
            codec = new LightJAXBCodec(JAXBContext.newInstance(LightRequest.class, LightResponse.class,
                    ImmutableAttributeMap.class, AttributeDefinition.class));
        } catch (JAXBException e) {
            log.error("Unable to instantiate in static initializer ",e);
        }
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
        eidasResponseCommunicationCache.put(tokenId, codec.marshall(lightResponse));
        log.info("LightResponse with ID: '{}' was saved. Cache: '{}'. LightResponse: '{}'", tokenId, eidasRequestCommunicationCache.getName(),  lightResponse.toString());
        return binaryLightToken;
    }

    public BinaryLightToken putErrorResponse(RequestDeniedException ex) throws SpecificCommunicationException {

        ILightResponse lightResponse = createILightResponseFailure(ex.getInResponseTo(),
                EIDASStatusCode.REQUESTER_URI, EIDASSubStatusCode.REQUEST_DENIED_URI, ex.getMessage(),
                getLightTokenResponseIssuerName());

        return putResponse(lightResponse);
    }

    public ILightRequest getAndRemoveRequest(final String tokenBase64) throws SpecificCommunicationException {

        try {
            final String tokenId = BinaryLightTokenHelper.getBinaryLightTokenId(tokenBase64,
                    lightTokenRequestSecret,
                    lightTokenRequestAlgorithm);


            String lightRequest = eidasRequestCommunicationCache.getAndRemove(tokenId);
            ILightRequest request = unmarshalRequest(lightRequest, eidasAttributeRegistry.getAttributes());
            if (request == null)
                throw new SpecificCommunicationException("The original request has expired or invalid ID was specified");

            log.info("Lightrequest found from cache for ID: '{}'. Cache: '{}'. Lightrequest: '{}'", tokenId, eidasRequestCommunicationCache.getName(),  request.toString());
            return request;
        } catch (SpecificCommunicationException | SecurityEIDASException e) {
            throw new BadRequestException("Invalid token", e);
        }
    }

    private ILightRequest unmarshalRequest(String lightRequestString, Collection<AttributeDefinition<?>> registry) throws SpecificCommunicationException {
        try {
            return codec.unmarshallRequest(lightRequestString, registry);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to unmarshal incoming request! " + e.getMessage(), e);
        }
    }

    private static ILightResponse createILightResponseFailure(
            String inResponseTo,
            EIDASStatusCode eidasStatusCode,
            EIDASSubStatusCode eidasSubStatusCode,
            String statusMessage, String issuer) {

        final ResponseStatus responseStatus = ResponseStatus.builder()
                .statusCode(eidasStatusCode.toString())
                .subStatusCode(eidasSubStatusCode.toString())
                .statusMessage(statusMessage)
                .failure(true)
                .build();

        return new LightResponse.Builder()
                .id(UUID.randomUUID().toString())
                .inResponseToId(inResponseTo)
                .issuer(issuer)
                .status(responseStatus).build();
    }
}

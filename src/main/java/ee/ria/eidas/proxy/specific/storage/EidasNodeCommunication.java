package ee.ria.eidas.proxy.specific.storage;

import com.google.common.collect.ImmutableSet;
import ee.ria.eidas.proxy.specific.error.BadRequestException;
import ee.ria.eidas.proxy.specific.error.RequestDeniedException;
import eu.eidas.auth.commons.EIDASStatusCode;
import eu.eidas.auth.commons.EIDASSubStatusCode;
import eu.eidas.auth.commons.attribute.*;
import eu.eidas.auth.commons.exceptions.SecurityEIDASException;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.light.impl.LightRequest;
import eu.eidas.auth.commons.light.impl.LightResponse;
import eu.eidas.auth.commons.light.impl.ResponseStatus;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import eu.eidas.specificcommunication.protocol.util.SecurityUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.xml.sax.SAXException;

import javax.annotation.PostConstruct;
import javax.cache.Cache;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.sax.SAXSource;
import java.io.StringWriter;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
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

    public ILightRequest getAndRemoveRequest(final String tokenBase64) {
        Assert.notNull(StringUtils.isNotEmpty(tokenBase64), "Token value cannot be null or empty!");

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

    private ILightRequest unmarshalRequest(String lightRequestString, Collection<AttributeDefinition<?>> registry) {
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

    @Slf4j
    public static class LightJAXBCodec {

        JAXBContext jaxbCtx;

        public LightJAXBCodec(JAXBContext jaxbCtx) {
            this.jaxbCtx = jaxbCtx;
        }

        public <T> String marshall(T input) throws SpecificCommunicationException {
            if (input == null) {
                return null;
            }
            StringWriter writer = new StringWriter();
            try {
                createMarshaller().marshal(input, writer);
            } catch (JAXBException e) {
                throw new SpecificCommunicationException(e);
            }
            return writer.toString();
        }

        public <T extends ILightRequest> T unmarshallRequest(String input,
                Collection<AttributeDefinition<?>> registry) throws SpecificCommunicationException {
            if (input == null) {
                return null;
            }
            if (registry == null) {
                throw new SpecificCommunicationException("missing registry");
            }
            try {
                SAXSource secureSaxSource = SecurityUtils.createSecureSaxSource(input);

                T unmarshalled = (T) createUnmarshaller().unmarshal(secureSaxSource);
                LightRequest.Builder resultBuilder = LightRequest.builder(unmarshalled);
                ImmutableAttributeMap.Builder mapBuilder = ImmutableAttributeMap.builder();

                for (ImmutableAttributeMap.ImmutableAttributeEntry<?> entry : unmarshalled.getRequestedAttributes().entrySet()) {
                    URI nameUri = entry.getKey().getNameUri();
                    AttributeDefinition<?> definition = getByName(nameUri, registry);

                    Iterable values = unmarshalValues(entry, definition);
                    mapBuilder.put(definition, values);
                }
                return (T) resultBuilder.requestedAttributes(mapBuilder.build()).build();
            } catch (JAXBException | AttributeValueMarshallingException
                    | SAXException
                    | ParserConfigurationException e) {
                throw new SpecificCommunicationException(e);
            }
        }

        private Iterable unmarshalValues(ImmutableAttributeMap.ImmutableAttributeEntry<?> entry, AttributeDefinition<?> definition)
                throws AttributeValueMarshallingException {
            ImmutableSet.Builder<AttributeValue<?>> valuesBuilder = ImmutableSet.builder();

            for (Object value : entry.getValues()) {
                AttributeValueMarshaller<?> valueMarshaller = definition.getAttributeValueMarshaller();
                boolean nonLatin = definition.isTransliterationMandatory();
                valuesBuilder.add(valueMarshaller.unmarshal(value.toString(), nonLatin));
            }
            return valuesBuilder.build();
        }

        private AttributeDefinition<? extends Object> getByName(URI nameUri,
                Collection<AttributeDefinition<?>> registry) throws SpecificCommunicationException {
            if (nameUri == null) {
                throw new SpecificCommunicationException("Invalid lookup nameUri");
            }
            for (Iterator<AttributeDefinition<?>> iterator = registry.iterator(); iterator.hasNext();) {
                AttributeDefinition<?> next = iterator.next();
                Assert.notNull(next.getNameUri(), String.format("Attribute with null nameUri: %s , present in the registry", next));
                if (next.getNameUri().equals(nameUri)) {
                    return next;
                }
            }
            throw new SpecificCommunicationException(String.format("Attribute %s not present in the registry", nameUri));
        }

        private Marshaller createMarshaller() throws JAXBException {
            Marshaller marshaller = jaxbCtx.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8"); // NOI18N
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            return marshaller;
        }

        private Unmarshaller createUnmarshaller() throws JAXBException {
            return jaxbCtx.createUnmarshaller();
        }

    }
}

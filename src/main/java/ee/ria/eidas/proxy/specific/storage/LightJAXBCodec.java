package ee.ria.eidas.proxy.specific.storage;

import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.specificcommunication.LightRequest;
import eu.eidas.specificcommunication.LightResponse;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import eu.eidas.specificcommunication.protocol.util.LightMessagesConverter;
import eu.eidas.specificcommunication.protocol.util.SecurityUtils;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.sax.SAXSource;
import java.io.StringWriter;
import java.util.Collection;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class LightJAXBCodec {
    private static final Class<?>[] LIGHT_REQUEST_CODEC = {LightRequest.class};
    private static final Class<?>[] LIGHT_RESPONSE_CODEC = {LightResponse.class};
    private final LightMessagesConverter messagesConverter = new LightMessagesConverter();
    private final JAXBContext lightRequestJAXBCtx;
    private final JAXBContext lightResponseJAXBCtx;

    LightJAXBCodec(JAXBContext lightRequestJAXBCtx, JAXBContext lightResponseJAXBCtx) {
        this.lightRequestJAXBCtx = lightRequestJAXBCtx;
        this.lightResponseJAXBCtx = lightResponseJAXBCtx;
    }

    public static LightJAXBCodec buildDefault() {
        JAXBContext lightRequestJAXBContext = getJAXBContext(LIGHT_REQUEST_CODEC);
        JAXBContext lightResponseJAXBContext = getJAXBContext(LIGHT_RESPONSE_CODEC);
        return new LightJAXBCodec(lightRequestJAXBContext, lightResponseJAXBContext);
    }

    private static JAXBContext getJAXBContext(Class<?>[] contextClasses) {
        try {
            return JAXBContext.newInstance(contextClasses);
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Unable to instantiate the JAXBContext", e);
        }
    }

    public String marshall(ILightRequest lightRequest) throws SpecificCommunicationException {
        LightRequest xmlLightRequest = messagesConverter.convert(lightRequest);
        return marshall(xmlLightRequest);
    }

    public String marshall(ILightResponse lightResponse) throws SpecificCommunicationException {
        LightResponse xmlLightResponse = messagesConverter.convert(lightResponse);
        return marshall(xmlLightResponse);
    }

    private <T> String marshall(T input) throws SpecificCommunicationException {
        if (input == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        try {
            createMarshaller(input.getClass()).marshal(input, writer);
        } catch (JAXBException e) {
            throw new SpecificCommunicationException(e);
        }
        return writer.toString();
    }

    public ILightRequest unmarshallRequest(String input, Collection<AttributeDefinition<?>> registry) throws SpecificCommunicationException {
        if (input == null) {
            return null;
        }
        if (registry == null) {
            throw new SpecificCommunicationException("Failed to unmarshal LightRequest! Missing attribute registry.");
        }
        try {
            SAXSource secureSaxSource = SecurityUtils.createSecureSaxSource(input);
            LightRequest rawRequest = (LightRequest) createUnmarshaller().unmarshal(secureSaxSource);
            return messagesConverter.convert(rawRequest, registry);
        } catch (JAXBException | ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Failed to unmarshal incoming request! " + e.getMessage(), e);
        }
    }

    private Marshaller createMarshaller(Class<?> srcType) throws JAXBException {
        Marshaller marshaller;
        if (LightRequest.class.isAssignableFrom(srcType)) {
            marshaller = lightRequestJAXBCtx.createMarshaller();
        } else {
            marshaller = lightResponseJAXBCtx.createMarshaller();
        }
        marshaller.setProperty(Marshaller.JAXB_ENCODING, UTF_8.name()); // NOI18N
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        return marshaller;
    }

    private Unmarshaller createUnmarshaller() throws JAXBException {
        return lightRequestJAXBCtx.createUnmarshaller();
    }
}

/* 
#   Copyright (c) 2017 European Commission  
#   Licensed under the EUPL, Version 1.2 or – as soon they will be 
#   approved by the European Commission - subsequent versions of the 
#    EUPL (the "Licence"); 
#    You may not use this work except in compliance with the Licence. 
#    You may obtain a copy of the Licence at: 
#    * https://joinup.ec.europa.eu/page/eupl-text-11-12  
#    *
#    Unless required by applicable law or agreed to in writing, software 
#    distributed under the Licence is distributed on an "AS IS" basis, 
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
#    See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package ee.ria.eidas.proxy.specific.util;

import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.attribute.impl.DateTimeAttributeValue;
import eu.eidas.auth.commons.attribute.impl.StringAttributeValue;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.light.impl.LightRequest;
import eu.eidas.auth.commons.light.impl.LightResponse;
import eu.eidas.auth.commons.light.impl.ResponseStatus;
import eu.eidas.auth.commons.protocol.eidas.impl.Gender;
import eu.eidas.auth.commons.protocol.eidas.impl.GenderAttributeValue;
import eu.eidas.auth.commons.protocol.eidas.impl.PostalAddress;
import eu.eidas.auth.commons.protocol.eidas.impl.PostalAddressAttributeValue;
import eu.eidas.auth.commons.protocol.eidas.spec.EidasSpec;
import eu.eidas.auth.commons.protocol.impl.SamlNameIdFormat;
import org.joda.time.DateTime;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class LightRequestTestHelper {

    public static final String UUID_REGEX = "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

    private LightRequestTestHelper() {}

    private static final PostalAddress pa = new PostalAddress.Builder()
            .adminUnitFirstLine("adminUnitFirstLine").adminUnitSecondLine("adminUnitSecondLine")
            .cvAddressArea("cvAddressArea").locatorDesignator("locatorDesignator")
            .locatorName("locatorName").poBox("poBox").postCode("postCode").postName("postName")
            .thoroughfare("thoroughfare")
            .build();

    public static final ImmutableAttributeMap NATURAL_PERSON_MANDATORY_ATTRIBUTES = new ImmutableAttributeMap.Builder()
            .put(EidasSpec.Definitions.PERSON_IDENTIFIER, new StringAttributeValue("Juncker-987654321"))
            .put(EidasSpec.Definitions.CURRENT_FAMILY_NAME, new StringAttributeValue("Juncker"))
            .put(EidasSpec.Definitions.CURRENT_GIVEN_NAME, new StringAttributeValue("Jean-Claude"), new StringAttributeValue("Jean" ), new StringAttributeValue("Claude"))
            .put(EidasSpec.Definitions.DATE_OF_BIRTH, new DateTimeAttributeValue(new DateTime()))
            .build();

    public static final ImmutableAttributeMap NATURAL_PERSON_OPTIONAL_ATTRIBUTES = new ImmutableAttributeMap.Builder()
            .put(EidasSpec.Definitions.BIRTH_NAME, new StringAttributeValue("Juncker"))
            .put(EidasSpec.Definitions.GENDER, new GenderAttributeValue(Gender.MALE))
            .put(EidasSpec.Definitions.PLACE_OF_BIRTH, new StringAttributeValue("Luxembourgh"))
            .put(EidasSpec.Definitions.CURRENT_ADDRESS, new PostalAddressAttributeValue(pa))
            .build();

    public static final ImmutableAttributeMap NATURAL_PERSON_ALL_ATTRIBUTES = new ImmutableAttributeMap.Builder()
            .putAll(NATURAL_PERSON_MANDATORY_ATTRIBUTES)
            .putAll(NATURAL_PERSON_OPTIONAL_ATTRIBUTES)
            .build();

    public static final ImmutableAttributeMap LEGAL_PERSON_MANDATORY_ATTRIBUTES = new ImmutableAttributeMap.Builder()
            .put(EidasSpec.Definitions.LEGAL_NAME, new StringAttributeValue(""))
            .put(EidasSpec.Definitions.LEGAL_PERSON_IDENTIFIER, new StringAttributeValue(""))
            .build();

    public static final ImmutableAttributeMap LEGAL_PERSON_OPTIONAL_ATTRIBUTES = new ImmutableAttributeMap.Builder()
            .put(EidasSpec.Definitions.LEGAL_PERSON_ADDRESS, new PostalAddressAttributeValue(pa))
            .put(EidasSpec.Definitions.VAT_REGISTRATION_NUMBER, new StringAttributeValue(""))
            .put(EidasSpec.Definitions.TAX_REFERENCE, new StringAttributeValue(""))
            .put(EidasSpec.Definitions.D_2012_17_EU_IDENTIFIER, new StringAttributeValue(""))
            .put(EidasSpec.Definitions.LEI, new StringAttributeValue(""))
            .put(EidasSpec.Definitions.EORI, new StringAttributeValue(""))
            .put(EidasSpec.Definitions.SEED, new StringAttributeValue(""))
            .put(EidasSpec.Definitions.SIC, new StringAttributeValue(""))
            .build();

    public static ILightRequest createLightRequest(String citizenCountry, String issuerName, String relayState, String loa, String spType, ImmutableAttributeMap requestedAttributes) {

        final LightRequest.Builder builder = LightRequest.builder()
                .id(UUID.randomUUID().toString())
                .citizenCountryCode(citizenCountry)
                .issuer(issuerName)
                .spType(spType)
                .relayState(relayState)
                .levelOfAssurance(loa)
                .requestedAttributes(requestedAttributes);

        return builder.build();

    }

    public static ILightResponse createLightResponse(String subject, String issuerName, String relayState, String loa) {
        final LightResponse.Builder builder = LightResponse.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuerName)
                .inResponseToId("123456")
                .status(ResponseStatus.builder().statusCode("urn:oasis:names:tc:SAML:2.0:status:Success").build())
                .subject(subject)
                .subjectNameIdFormat(SamlNameIdFormat.UNSPECIFIED.getNameIdFormat())
                .subject(subject)
                .relayState(relayState)
                .levelOfAssurance(loa);

        return builder.build();
    }

    public static ILightRequest createLightRequest(ImmutableAttributeMap requestedAttributes) {
        return createLightRequest("citizenCountry", "issuerName", "relayState", "http://eidas.europa.eu/LoA/high", "public", requestedAttributes);
    }

    public static ILightRequest createDefaultLightRequest() {
        return createLightRequest("citizenCountry", "issuerName", "relayState", "http://eidas.europa.eu/LoA/high", "public", NATURAL_PERSON_ALL_ATTRIBUTES);
    }

    public static ILightResponse createDefaultLightResponse() {
        return createLightResponse("EE1010101010", "issuerName", "relayState", "http://eidas.europa.eu/LoA/high");
    }

    public static <T> List<T> getListFromIterator(Iterator<T> iterator) {
        Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false)
                .collect(Collectors.toList());
    }

    public static Element getXmlDocument(String xml) throws SAXException, IOException, ParserConfigurationException {
        return DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes()))
                .getDocumentElement();
    }
}

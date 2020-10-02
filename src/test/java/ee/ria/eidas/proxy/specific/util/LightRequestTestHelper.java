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
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class LightRequestTestHelper {

    public static final String UUID_REGEX = "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";
    public static final String IP_REGEX = "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$";
    public static final String MOCK_SUBJECT_IDENTIFIER = "EE1010101010";
    public static final String MOCK_ISSUER_NAME = "issuerName";
    public static final String MOCK_RELAY_STATE = "relayState";
    public static final String MOCK_LOA_HIGH = "http://eidas.europa.eu/LoA/high";
    public static final String MOCK_CITIZEN_COUNTRY = "citizenCountry";
    public static final String MOCK_SP_TYPE = "public";
    public static final String MOCK_PROVIDER_NAME = "SP Name & correctly escaped";

    private LightRequestTestHelper() {}

    static {
        DateTimeZone.setDefault(DateTimeZone.UTC);
    }

    private static final PostalAddress pa = new PostalAddress.Builder()
            .adminUnitFirstLine("adminUnitFirstLine").adminUnitSecondLine("adminUnitSecondLine")
            .cvAddressArea("cvAddressArea").locatorDesignator("locatorDesignator")
            .locatorName("locatorName").poBox("poBox").postCode("postCode").postName("postName")
            .thoroughfare("thoroughfare")
            .build();

    public static final ImmutableAttributeMap NATURAL_PERSON_MANDATORY_ATTRIBUTES = new ImmutableAttributeMap.Builder()
            .put(EidasSpec.Definitions.PERSON_IDENTIFIER, new StringAttributeValue("60001019906"))
            .put(EidasSpec.Definitions.CURRENT_FAMILY_NAME, new StringAttributeValue("O’CONNEŽ-ŠUSLIK TESTNUMBER"))
            .put(EidasSpec.Definitions.CURRENT_GIVEN_NAME, new StringAttributeValue("MARY ÄNN"))
            .put(EidasSpec.Definitions.DATE_OF_BIRTH, new DateTimeAttributeValue(DateTime.parse("2000-01-01",
                    DateTimeFormat.forPattern("yyyy-MM-dd"))))
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

    public static ILightRequest createLightRequest(String citizenCountry, String issuerName, String relayState, String loa, String spType, String spName, ImmutableAttributeMap requestedAttributes, String nameIdFormat) {

        final LightRequest.Builder builder = LightRequest.builder()
                .id(UUID.randomUUID().toString())
                .citizenCountryCode(citizenCountry)
                .issuer(issuerName)
                .spType(spType)
                .providerName(spName)
                .relayState(relayState)
                .levelOfAssurance(loa)
                .nameIdFormat(nameIdFormat)
                .requestedAttributes(requestedAttributes);

        return builder.build();

    }

    public static ILightResponse createLightResponse(String subject, String issuerName, String relayState, String loa) {
        final LightResponse.Builder builder = LightResponse.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuerName)
                .ipAddress("123.123.123.321")
                .inResponseToId("123456")
                .status(ResponseStatus.builder().statusCode("urn:oasis:names:tc:SAML:2.0:status:Success").build())
                .subject(subject)
                .subjectNameIdFormat(SamlNameIdFormat.UNSPECIFIED.getNameIdFormat())
                .subject(subject)
                .relayState(relayState)
                .levelOfAssurance(loa)
                .attributes(NATURAL_PERSON_MANDATORY_ATTRIBUTES);

        return builder.build();
    }

    public static ILightRequest createDefaultLightRequest(String nameIdFormat) {
        return createLightRequest(MOCK_CITIZEN_COUNTRY, MOCK_ISSUER_NAME, MOCK_RELAY_STATE, MOCK_LOA_HIGH, MOCK_SP_TYPE,
                MOCK_PROVIDER_NAME, NATURAL_PERSON_ALL_ATTRIBUTES, nameIdFormat);
    }

    public static ILightRequest createLightRequest(ImmutableAttributeMap requestedAttributes) {
        return createLightRequest("citizenCountry", MOCK_ISSUER_NAME, MOCK_RELAY_STATE, MOCK_LOA_HIGH,
                MOCK_SP_TYPE, MOCK_PROVIDER_NAME, requestedAttributes, null);
    }

    public static ILightRequest createDefaultLightRequest() {
        return createLightRequest(MOCK_CITIZEN_COUNTRY, MOCK_ISSUER_NAME, MOCK_RELAY_STATE, MOCK_LOA_HIGH, MOCK_SP_TYPE,
                MOCK_PROVIDER_NAME, NATURAL_PERSON_ALL_ATTRIBUTES, null);
    }

    public static ILightResponse createDefaultLightResponse() {
        return createLightResponse(MOCK_SUBJECT_IDENTIFIER, MOCK_ISSUER_NAME, MOCK_RELAY_STATE, MOCK_LOA_HIGH);
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
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
    }
}

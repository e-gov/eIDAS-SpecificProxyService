package ee.ria.eidas.proxy.specific.web;

import com.nimbusds.oauth2.sdk.util.URLUtils;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.attribute.PersonType;
import eu.eidas.auth.commons.attribute.impl.LiteralStringAttributeValueMarshaller;
import eu.eidas.auth.commons.attribute.impl.StringAttributeValue;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.impl.LightRequest;
import eu.eidas.auth.commons.protocol.eidas.spec.EidasSpec;
import eu.eidas.auth.commons.protocol.eidas.spec.NaturalPersonSpec;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import io.restassured.response.Response;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;

import javax.cache.Cache;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.*;
import static ee.ria.eidas.proxy.specific.web.ProxyServiceRequestController.ENDPOINT_PROXY_SERVICE_REQUEST;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestMethodOrder(MethodOrderer.Alphanumeric.class)
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = ProxyServiceRequestControllerTests.TestContextInitializer.class)
public class ProxyServiceRequestLightRequestAcceptanceTests extends ControllerTest {

    @Test
    void badRequestWhen_InvalidCitizenCountryCode() throws Exception {
        ILightRequest mockLightRequest = LightRequest.builder()
                .id(UUID.randomUUID().toString())
                .issuer(MOCK_ISSUER_NAME)
                .citizenCountryCode("not 3166-1-alpha-2 format")
                .spType(MOCK_SP_TYPE)
                .providerName(MOCK_PROVIDER_NAME)
                .relayState(MOCK_RELAY_STATE)
                .levelOfAssurance(MOCK_LOA_HIGH)
                .nameIdFormat("unknown")
                .build();

        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(400)
                .body("error", equalTo("Bad Request"))
                .body("errors", nullValue())
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("CitizenCountryCode not in 3166-1-alpha-2 format"));
        assertErrorIsLogged("Bad request: CitizenCountryCode not in 3166-1-alpha-2 format");
    }

    @Test
    void badRequestWhen_InvalidNameIdFormat() throws Exception {
        ILightRequest mockLightRequest = LightRequest.builder()
                .id(UUID.randomUUID().toString())
                .issuer(MOCK_ISSUER_NAME)
                .citizenCountryCode("EE")
                .spType(MOCK_SP_TYPE)
                .providerName(MOCK_PROVIDER_NAME)
                .relayState(MOCK_RELAY_STATE)
                .levelOfAssurance(MOCK_LOA_HIGH)
                .nameIdFormat("unknown")
                .build();

        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(400)
                .body("error", equalTo("Bad Request"))
                .body("errors", nullValue())
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Invalid NameIdFormat"));
        assertErrorIsLogged("Bad request: Invalid NameIdFormat");
    }

    @Test
    void badRequestWhen_Replay() throws Exception {
        ILightRequest mockLightRequest = createLightRequest(NATURAL_PERSON_MANDATORY_ATTRIBUTES);
        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        Response response = given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(302)
                .header(HttpHeaders.LOCATION, startsWith("https://localhost:9877/oidc/authorize"))
                .extract().response();

        // assert redirect parameters
        assertValidOidcAuthenticationRequest(response);
        assertScopeParameter(response, "openid idcard mid");

        // assert request communication cache
        assertRequestInIdpCommunicationCache(mockLightRequest);
        assertResponseCommunicationCacheIsEmpty();

        // try to replay
        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(400)
                .body("error", equalTo("Bad Request"))
                .body("errors", nullValue())
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Invalid token"));
    }

    private void assertValidOidcAuthenticationRequest(Response response) throws MalformedURLException {
        Map<String, List<String>> urlParameters = URLUtils.parseParameters(new URL(response.getHeader(HttpHeaders.LOCATION)).getQuery());
        assertEquals(7, urlParameters.size());
        assertNotNull(urlParameters.get("scope").get(0));
        assertEquals("code", urlParameters.get("response_type").get(0));
        assertEquals("openIdDemo", urlParameters.get("client_id").get(0));
        assertEquals("https://localhost:9877/redirect", urlParameters.get("redirect_uri").get(0));
        assertEquals("high", urlParameters.get("acr_values").get(0));
        assertEquals("et", urlParameters.get("ui_locales").get(0));
        assertThat(urlParameters.get("state").get(0), matchesPattern(UUID_REGEX));
    }

    private void assertScopeParameter(Response response, String openid_idcard_mid) throws MalformedURLException {
        Map<String, List<String>> urlParameters = URLUtils.parseParameters(new URL(response.getHeader(HttpHeaders.LOCATION)).getQuery());
        assertEquals(openid_idcard_mid, urlParameters.get("scope").get(0));
    }

    private void assertRequestInIdpCommunicationCache(ILightRequest mockLightRequest) {
        List<Cache.Entry<String, SpecificProxyServiceCommunication.CorrelatedRequestsHolder>> list = getListFromIterator(getIdpRequestCommunicationCache().iterator());
        assertEquals(1, list.size());
        assertThat(list.get(0).getKey(), matchesPattern(UUID_REGEX));

        ILightRequest cachedLightRequest = list.get(0).getValue().getLightRequest();
        assertEquals(mockLightRequest.getCitizenCountryCode(), cachedLightRequest.getCitizenCountryCode());
        assertEquals(mockLightRequest.getId(), cachedLightRequest.getId());
        assertEquals(mockLightRequest.getLevelOfAssurance(), cachedLightRequest.getLevelOfAssurance());
        assertEquals(mockLightRequest.getNameIdFormat(), cachedLightRequest.getNameIdFormat());
        assertEquals(mockLightRequest.getSpType(), cachedLightRequest.getSpType());
        assertEquals(mockLightRequest.getProviderName(), cachedLightRequest.getProviderName());
        assertEquals(mockLightRequest.getRelayState(), cachedLightRequest.getRelayState());

        assertEquals(
                getFriendlyNamesList(mockLightRequest.getRequestedAttributes()),
                getFriendlyNamesList(cachedLightRequest.getRequestedAttributes())
        );
    }

    private List<String> getFriendlyNamesList(ImmutableAttributeMap attributes) {
        return attributes.getDefinitions().stream().map(AttributeDefinition::getFriendlyName).collect(Collectors.toList());
    }

    @Test
    void internalServerErrorWhen_RequestedAttributesNotSupported() throws Exception {
        ImmutableAttributeMap customAttributes = new ImmutableAttributeMap.Builder()
                .putAll(NATURAL_PERSON_MANDATORY_ATTRIBUTES)
                .put(getCustomAttributeDefinition("UnknownAttribute"), new StringAttributeValue("")).build();

        BinaryLightToken mockBinaryLightToken = putRequest(createLightRequest(customAttributes));

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(500)
                .body("error", equalTo("Internal Server Error"))
                .body("errors", nullValue())
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

        assertErrorIsLogged("Server encountered an unexpected error: Missing registry");
        assertResponseCommunicationCacheIsEmpty();
    }

    private AttributeDefinition<String> getCustomAttributeDefinition(String friendlyName) {
        return AttributeDefinition.<String>builder()
                .nameUri(NaturalPersonSpec.Namespace.URI + "/UnknownAttribute")
                .friendlyName(friendlyName)
                .personType(PersonType.NATURAL_PERSON)
                .required(true)
                .uniqueIdentifier(true)
                .xmlType(NaturalPersonSpec.Namespace.URI, "UnknownAttribute", NaturalPersonSpec.Namespace.PREFIX)
                .attributeValueMarshaller(new LiteralStringAttributeValueMarshaller())
                .build();
    }

    @Test
    void internalServerErrorWhen_CitizenCountryNotSet(@Value("classpath:__files/mock_requests/light-request-missing-citizen-country.xml") Resource lightRequest) {
        BinaryLightToken mockBinaryLightToken = putRequest(lightRequest);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(500)
                .body("error", equalTo("Internal Server Error"))
                .body("errors", nullValue())
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

        assertErrorIsLogged("Server encountered an unexpected error: citizenCountryCode cannot be null, empty or blank");
    }

    @Test
    void internalServerErrorWhen_IdNotSet(@Value("classpath:__files/mock_requests/light-request-missing-id.xml") Resource lightRequest) {
        BinaryLightToken mockBinaryLightToken = putRequest(lightRequest);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(500)
                .body("error", equalTo("Internal Server Error"))
                .body("errors", nullValue())
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

        assertErrorIsLogged("Server encountered an unexpected error: id cannot be null, empty or blank");
    }

    @Test
    void internalServerErrorWhen_IssuerNotSet(@Value("classpath:__files/mock_requests/light-request-missing-issuer.xml") Resource lightRequest) {
        BinaryLightToken mockBinaryLightToken = putRequest(lightRequest);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(500)
                .body("error", equalTo("Internal Server Error"))
                .body("errors", nullValue())
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

        assertErrorIsLogged("Server encountered an unexpected error: issuer cannot be null, empty or blank");
    }

    @Test
    void internalServerErrorWhen_InvalidXml(@Value("classpath:__files/mock_requests/light-request-invalid-xml.xml") Resource lightRequest) {
        BinaryLightToken mockBinaryLightToken = putRequest(lightRequest);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(500)
                .body("error", equalTo("Internal Server Error"))
                .body("errors", nullValue())
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

        assertErrorIsLogged("Server encountered an unexpected error: Failed to unmarshal incoming request! unexpected " +
                "element (uri:\"\", local:\"lightRequest1\"). Expected elements are <{http://cef.eidas.eu/LightRequest}lightRequest>");
    }

    @Test
    void redirectToEidasnodeWithErrorWhen_InvalidSpType() throws Exception {
        ILightRequest mockLightRequest = LightRequest.builder()
                .id(UUID.randomUUID().toString())
                .issuer(MOCK_ISSUER_NAME)
                .citizenCountryCode("EE")
                .spType("unknown")
                .providerName(MOCK_PROVIDER_NAME)
                .relayState(MOCK_RELAY_STATE)
                .levelOfAssurance(MOCK_LOA_HIGH)
                .build();

        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(302)
                .header(HttpHeaders.LOCATION, startsWith("https://ee-eidas-proxy:8083/EidasNode/SpecificProxyServiceResponse?token=c3BlY2lmaW"));
        assertResponseCommunicationCacheContainsUserCancelResponse("Service provider type not supported. Allowed types: [public]", mockLightRequest.getId());
    }

    @Test
    void redirectToEidasnodeWithErrorWhen_SpTypeNotSet() throws Exception {
        ILightRequest mockLightRequest = LightRequest.builder()
                .id(UUID.randomUUID().toString())
                .issuer(MOCK_ISSUER_NAME)
                .citizenCountryCode("EE")
                .providerName(MOCK_PROVIDER_NAME)
                .relayState(MOCK_RELAY_STATE)
                .levelOfAssurance(MOCK_LOA_HIGH)
                .build();

        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(302)
                .header(HttpHeaders.LOCATION, startsWith("https://ee-eidas-proxy:8083/EidasNode/SpecificProxyServiceResponse?token=c3BlY2lmaW"));
        assertResponseCommunicationCacheContainsUserCancelResponse("Service provider type not supported. Allowed types: [public]", mockLightRequest.getId());
    }

    @Test
    void redirectToIdpWhenValidRequest_AttributesNotSet() throws SpecificCommunicationException {
        ILightRequest mockLightRequest = LightRequest.builder()
                .id(UUID.randomUUID().toString())
                .issuer(MOCK_ISSUER_NAME)
                .citizenCountryCode("EE")
                .spType(MOCK_SP_TYPE)
                .providerName(MOCK_PROVIDER_NAME)
                .relayState(MOCK_RELAY_STATE)
                .levelOfAssurance(MOCK_LOA_HIGH)
                .build();

        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(302)
                .header(HttpHeaders.LOCATION, startsWith("https://localhost:9877/oidc/authorize"))
                .extract().response();
    }

    @Test
    void redirectToIdpWhenValidRequest_LegalPersonAllAttributes() throws Exception {
        ILightRequest mockLightRequest = createLightRequest(new ImmutableAttributeMap.Builder()
                .putAll(LEGAL_PERSON_MANDATORY_ATTRIBUTES).putAll(LEGAL_PERSON_OPTIONAL_ATTRIBUTES).build());
        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        Response response = given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(302)
                .header(HttpHeaders.LOCATION, startsWith("https://localhost:9877/oidc/authorize"))
                .extract().response();

        // assert redirect parameters
        assertValidOidcAuthenticationRequest(response);
        assertScopeParameter(response, "openid idcard mid legalperson");

        // assert request communication cache
        assertRequestInIdpCommunicationCache(mockLightRequest);
    }

    @Test
    void redirectToIdpWhenValidRequest_LegalPersonMandatoryAttributesOnly() throws Exception {
        ILightRequest mockLightRequest = createLightRequest(LEGAL_PERSON_MANDATORY_ATTRIBUTES);
        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        Response response = given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(302)
                .header(HttpHeaders.LOCATION, startsWith("https://localhost:9877/oidc/authorize"))
                .extract().response();

        // assert redirect parameters
        assertValidOidcAuthenticationRequest(response);
        assertScopeParameter(response, "openid idcard mid legalperson");

        // assert request communication cache
        assertRequestInIdpCommunicationCache(mockLightRequest);
    }

    @Test
    void redirectToIdpWhenValidRequest_NaturalPersonAllAttributes() throws Exception {
        ILightRequest mockLightRequest = createLightRequest(new ImmutableAttributeMap.Builder()
                .putAll(NATURAL_PERSON_MANDATORY_ATTRIBUTES)
                .putAll(NATURAL_PERSON_OPTIONAL_ATTRIBUTES)
                .put(EidasSpec.Definitions.REPV_PERSON_IDENTIFIER, new StringAttributeValue("")) // should be ignored
                .build());
        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        Response response = given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(302)
                .header(HttpHeaders.LOCATION, startsWith("https://localhost:9877/oidc/authorize"))
                .extract().response();

        // assert OIDC redirect URL
        assertValidOidcAuthenticationRequest(response);
        assertScopeParameter(response, "openid idcard mid");

        // assert request communication cache
        assertRequestInIdpCommunicationCache(mockLightRequest);
    }

    @Test
    void redirectToIdpWhenValidRequest_NaturalPersonMandatoryAttributesOnly_GET() throws Exception {
        ILightRequest mockLightRequest = createLightRequest(NATURAL_PERSON_MANDATORY_ATTRIBUTES);
        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        Response response = given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(302)
                .header(HttpHeaders.LOCATION, startsWith("https://localhost:9877/oidc/authorize"))
                .extract().response();

        // assert redirect parameters
        assertValidOidcAuthenticationRequest(response);
        assertScopeParameter(response, "openid idcard mid");

        // assert request communication cache
        assertRequestInIdpCommunicationCache(mockLightRequest);
        assertResponseCommunicationCacheIsEmpty();
    }

    @Test
    void redirectToIdpWhenValidRequest_NaturalPersonMandatoryAttributesOnly_POST() throws Exception {
        ILightRequest mockLightRequest = createLightRequest(NATURAL_PERSON_MANDATORY_ATTRIBUTES);
        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        Response response = given()
                .formParams(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .post(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(302)
                .header(HttpHeaders.LOCATION, startsWith("https://localhost:9877/oidc/authorize"))
                .extract().response();

        // assert redirect parameters
        assertValidOidcAuthenticationRequest(response);
        assertScopeParameter(response, "openid idcard mid");

        // assert request communication cache
        assertRequestInIdpCommunicationCache(mockLightRequest);
        assertResponseCommunicationCacheIsEmpty();
    }

    @Test
    void redirectToIdpWhenValidRequest_ProviderNotSet() throws Exception {
        ILightRequest mockLightRequest = LightRequest.builder()
                .id(UUID.randomUUID().toString())
                .issuer(MOCK_ISSUER_NAME)
                .citizenCountryCode("EE")
                .spType(MOCK_SP_TYPE)
                .relayState(MOCK_RELAY_STATE)
                .levelOfAssurance(MOCK_LOA_HIGH)
                .build();

        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .header(HttpHeaders.LOCATION, startsWith("https://localhost:9877/oidc/authorize"))
                .statusCode(302);
    }

    @Test
    void redirectToIdpWhenValidRequest_RelayStateNotSet() throws Exception {
        ILightRequest mockLightRequest = LightRequest.builder()
                .id(UUID.randomUUID().toString())
                .issuer(MOCK_ISSUER_NAME)
                .citizenCountryCode("EE")
                .spType(MOCK_SP_TYPE)
                .providerName(MOCK_PROVIDER_NAME)
                .levelOfAssurance(MOCK_LOA_HIGH)
                .build();

        BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .header(HttpHeaders.LOCATION, startsWith("https://localhost:9877/oidc/authorize"))
                .statusCode(302);
    }
}

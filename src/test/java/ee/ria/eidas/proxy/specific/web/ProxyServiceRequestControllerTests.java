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
import io.restassured.response.Response;
import org.apache.http.HttpHeaders;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest( webEnvironment = RANDOM_PORT )
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = ProxyServiceRequestControllerTests.TestContextInitializer.class )
class ProxyServiceRequestControllerTests extends ControllerTest {

	@Test
	void methodNotAllowedWhenInvalidHttpMethod() {
		assertHttpMethodsNotAllowed(ENDPOINT_PROXY_SERVICE_REQUEST, "PUT", "DELETE", "CONNECT", "OPTIONS", "PATCH", "CUSTOM", "HEAD", "TRACE");
	}

	@Test
	void badRequestWhenMissingRequiredParameters_token() {
		given()
			.param("invalidParameter", "invalidParameterValue")
		.when()
			.get(ENDPOINT_PROXY_SERVICE_REQUEST)
		.then()
			.assertThat()
			.statusCode(400)
			.body("error", equalTo("Bad Request"))
			.body("errors", equalTo("Parameter 'token': must not be null"))
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Validation failed for object='requestParameters'. Error count: 1"));

		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void badRequestWhenInvalidParameterValue_invalidCharacters() {
		given()
			.param(EidasParameterKeys.TOKEN.toString(), "'@´<>?")
		.when()
			.get(ENDPOINT_PROXY_SERVICE_REQUEST)
		.then()
			.assertThat()
			.statusCode(400)
			.body("error", equalTo("Bad Request"))
			.body("errors", equalTo("Parameter 'token[0]': only base64 characters allowed"))
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Validation failed for object='requestParameters'. Error count: 1"));

		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void badRequestWhenInvalidParameterValue_invalidLightTokenSignature() {
		given()
			.param(EidasParameterKeys.TOKEN.toString(), "YXNkZmZzZGZzZGZkc2ZzZmQ")
		.when()
			.get(ENDPOINT_PROXY_SERVICE_REQUEST)
		.then()
			.assertThat()
			.statusCode(400)
			.body("error", equalTo("Bad Request"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Invalid token"));

		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void badRequestWhenInvalidParameterValue_multipleParameterValuesNotSupported() throws Exception {
		BinaryLightToken mockBinaryLightToken = putRequest(createDefaultLightRequest());
		String tokenBase64 = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken);

		given()
			.param(EidasParameterKeys.TOKEN.toString(), tokenBase64)
			.param(EidasParameterKeys.TOKEN.toString(), tokenBase64)
		.when()
			.get(ENDPOINT_PROXY_SERVICE_REQUEST)
		.then()
			.assertThat()
			.statusCode(400)
			.body("error", equalTo("Bad Request"))
			.body("errors", equalTo("Parameter 'token': using multiple instances of parameter is not allowed"))
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Validation failed for object='requestParameters'. Error count: 1"));

		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void badRequestWhenInvalidLightToken_invalidValueOrExpired() throws Exception {
		ILightRequest defaultLightRequest = createDefaultLightRequest();
		BinaryLightToken mockBinaryLightToken = putRequest(defaultLightRequest);
		String tokenBase64 = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken);
		getEidasNodeRequestCommunicationCache().getAndRemove(mockBinaryLightToken.getToken().getId());

		given()
			.param(EidasParameterKeys.TOKEN.toString(), tokenBase64)
		.when()
			.get(ENDPOINT_PROXY_SERVICE_REQUEST)
		.then()
			.assertThat()
			.statusCode(400)
			.body("error", equalTo("Bad Request"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Invalid token"));

		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void badRequestWhenReplay() throws Exception {
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
		assertScopeParameter(response, "openid idcard mid " +
				"eidas:attribute:person_identifier " +
				"eidas:attribute:family_name " +
				"eidas:attribute:first_name " +
				"eidas:attribute:date_of_birth");

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

	@Test
	void internalServerErrorWhenRequestedAttributesNotSupported() throws Exception {
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

		assertErrorIsLogged("Server encountered an unexpected error: Failed to unmarshal incoming request! Attribute http://eidas.europa.eu/attributes/naturalperson/UnknownAttribute not present in the registry");
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenRequestedLoANotSupported() throws Exception {

		LightRequest request = LightRequest.builder()
				.id(UUID.randomUUID().toString())
				.citizenCountryCode(MOCK_CITIZEN_COUNTRY)
				.issuer(MOCK_ISSUER_NAME)
				.spType(MOCK_SP_TYPE)
				.levelOfAssurance("INVALID_LOA")
				.requestedAttributes(NATURAL_PERSON_MANDATORY_ATTRIBUTES).build();

		BinaryLightToken mockBinaryLightToken = putRequest(request);

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

		assertErrorIsLogged("Server encountered an unexpected error: Invalid level of assurance value. Allowed values: http://eidas.europa.eu/LoA/low, http://eidas.europa.eu/LoA/substantial, http://eidas.europa.eu/LoA/high");
		assertResponseCommunicationCacheIsEmpty();
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
		assertScopeParameter(response, "openid idcard mid " +
				"eidas:attribute:person_identifier " +
				"eidas:attribute:family_name " +
				"eidas:attribute:first_name " +
				"eidas:attribute:date_of_birth");

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
		assertScopeParameter(response, "openid idcard mid " +
				"eidas:attribute:person_identifier " +
				"eidas:attribute:family_name " +
				"eidas:attribute:first_name " +
				"eidas:attribute:date_of_birth");

		// assert request communication cache
		assertRequestInIdpCommunicationCache(mockLightRequest);
		assertResponseCommunicationCacheIsEmpty();
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
		assertScopeParameter(response, "openid idcard mid " +
				"eidas:attribute:person_identifier " +
				"eidas:attribute:family_name " +
				"eidas:attribute:first_name " +
				"eidas:attribute:date_of_birth " +
				"eidas:attribute:birth_name " +
				"eidas:attribute:gender " +
				"eidas:attribute:place_of_birth " +
				"eidas:attribute:current_address");

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
		assertScopeParameter(response, "openid idcard mid " +
				"eidas:attribute:legal_name " +
				"eidas:attribute:legal_person_identifier");

		// assert request communication cache
		assertRequestInIdpCommunicationCache(mockLightRequest);
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
		assertScopeParameter(response, "openid idcard mid " +
				"eidas:attribute:legal_name " +
				"eidas:attribute:legal_person_identifier " +
				"eidas:attribute:legal_address " +
				"eidas:attribute:vat_registration " +
				"eidas:attribute:tax_reference " +
				"eidas:attribute:business_codes " +
				"eidas:attribute:lei " +
				"eidas:attribute:eori " +
				"eidas:attribute:seed " +
				"eidas:attribute:sic");

		// assert request communication cache
		assertRequestInIdpCommunicationCache(mockLightRequest);
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

	private void assertScopeParameter(Response response, String openid_idcard_mid) throws  MalformedURLException {
		Map<String, List<String>> urlParameters = URLUtils.parseParameters(new URL(response.getHeader(HttpHeaders.LOCATION)).getQuery());
		assertEquals(openid_idcard_mid, urlParameters.get("scope").get(0));
	}

	private void assertRequestInIdpCommunicationCache(ILightRequest mockLightRequest) {
		List<Cache.Entry<String, SpecificProxyServiceCommunication.CorrelatedRequestsHolder>> list = getListFromIterator(getIdpRequestCommunicationCache().iterator());
		assertEquals(1, list.size());
		assertThat(list.get(0).getKey(), matchesPattern(UUID_REGEX));

		ILightRequest cachedLightRequest = list.get(0).getValue().getLightRequest();
		assertEquals(mockLightRequest.getCitizenCountryCode(),cachedLightRequest.getCitizenCountryCode());
		assertEquals(mockLightRequest.getId(),cachedLightRequest.getId());
		assertEquals(mockLightRequest.getLevelOfAssurance(),cachedLightRequest.getLevelOfAssurance());
		assertEquals(mockLightRequest.getNameIdFormat(),cachedLightRequest.getNameIdFormat());
		assertEquals(mockLightRequest.getSpType(),cachedLightRequest.getSpType());
		assertEquals(mockLightRequest.getProviderName(),cachedLightRequest.getProviderName());
		assertEquals(mockLightRequest.getRelayState(),cachedLightRequest.getRelayState());

		assertEquals(
				getFriendlyNamesList(mockLightRequest.getRequestedAttributes()),
				getFriendlyNamesList(cachedLightRequest.getRequestedAttributes())
		);
	}

	private List<String> getFriendlyNamesList(ImmutableAttributeMap attributes) {
		return attributes.getDefinitions().stream().map(AttributeDefinition::getFriendlyName).collect(Collectors.toList());
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
}





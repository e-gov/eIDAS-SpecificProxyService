package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication.CorrelatedRequestsHolder;
import eu.eidas.auth.commons.light.ILightRequest;
import io.restassured.RestAssured;
import io.restassured.builder.ResponseSpecBuilder;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.createDefaultLightRequest;
import static ee.ria.eidas.proxy.specific.web.IdpResponseController.ENDPOINT_IDP_RESPONSE;
import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.StringStartsWith.startsWith;

abstract class IdpResponseControllerTests extends ControllerTest {

	@Value("${lightToken.proxyservice.response.issuer.name}")
	private String lightTokenResponseIssuerName;

	@ParameterizedTest
	@ValueSource(strings = {"state", "code", "error", "errorDescription"})
	void badRequestWhen_InvalidParameterSize(String paramName) {
		given()
			.param(paramName,
					RandomStringUtils.randomAlphanumeric(1001))
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("error", equalTo("Bad Request"))
			.body("errors", containsString("Parameter '" + paramName + "[0]': size must be between 1 and 1000"))
			.body("incidentNumber", notNullValue())
			.body("message", containsString("Validation failed for object='idpCallbackRequest'. Error count: "));
	}

	@Test
	void badRequestWhen_MissingRequiredParam_state() {
		given()
				.param("sas", "asas")
				.when()
				.get(ENDPOINT_IDP_RESPONSE)
				.then()
				.assertThat()
				.statusCode(400)
				.body("error", equalTo("Bad Request"))
				.body("errors", equalTo("Parameter 'state': must not be empty"))
				.body("incidentNumber", notNullValue())
				.body("message", equalTo("Validation failed for object='idpCallbackRequest'. Error count: 1"));
	}

	@Test
	void badRequestWhen_DuplicateParams_state() {
		given()
			.param("state", ".,.")
			.param("state", "...")
			.param("state", "...")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("error", equalTo("Bad Request"))
			.body("errors", equalTo("Parameter 'state': using multiple instances of parameter is not allowed"))
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Validation failed for object='idpCallbackRequest'. Error count: 1"));
	}

	@Test
	void badRequestWhen_MissingRequiredParam_missingBothCodeAndError() {
		given()
			.param("state", "...")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("error", equalTo("Bad Request"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Either error or code parameter is required"));
	}

	@Test
	void badRequestWhen_HavingBothCodeAndErrorParameters() {
		given()
			.param("state", "...")
			.param("code", "...")
			.param("error", "...")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("error", equalTo("Bad Request"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Either error or code parameter can be present in a callback request. Both code and error parameters found"));
	}

	@Test
	void badRequestWhen_InvalidOrNonexistingState_successResponse() {
		given()
			.param("state", "invalidState")
			.param("code", "1234567890abcdefghij")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
				.body("error", equalTo("Bad Request"))
				.body("errors", nullValue())
				.body("incidentNumber", notNullValue())
				.body("message", equalTo("Invalid state"));
	}

	@Test
	void badRequestWhen_InvalidOrNonxistingState_errorResponse() {
		given()
			.param("state", "invalidState")
			.param("error", "user_cancel")
			.param("error_description", "User canceled")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("error", equalTo("Bad Request"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Invalid state"));
	}

	@Test
	void invalidMethodsNotAllowed() {
		assertHttpMethodsNotAllowed(ENDPOINT_IDP_RESPONSE, "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "CUSTOM", "HEAD", "TRACE");
	}

	@Test
	void ConnectHttpMethodRespondsWith501() {
		RestAssured.responseSpecification = new ResponseSpecBuilder().build();

		given()
				.when()
				.request("CONNECT", ENDPOINT_IDP_RESPONSE)
				.then()
				.assertThat()
				.statusCode(501);
	}

	@Test
	void internalServerErrorWhenIdpError_InvalidAuthenticationRequest() throws Exception {

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("error", "invalid_request")
			.param("state", mapEntry.getKey())
			.config(RestAssured.config().redirect(redirectConfig().followRedirects(false)))
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: OIDC authentication request has returned an error (code = 'invalid_request', description = 'null')");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_UnexpectedContent() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBody("<xml/>")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: Invalid OIDC token endpoint response! Invalid JSON");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_InvalidTokenType() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_invalid-token-type.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: Invalid OIDC token endpoint response! Unsupported token_type: mac");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_IdTokenHasExpired() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_id-token-expired.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: Error when validating id_token! Expired JWT");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_IdTokenNotYetValid() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_id-token-not-yet-valid.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: Error when validating id_token! JWT issue time ahead of current time");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_InvalidLoa() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_invalid-id-token-acr.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: Invalid level of assurance in IDP response. Authentication was requested with level 'http://eidas.europa.eu/LoA/high', but IDP response level is 'http://eidas.europa.eu/LoA/low'.");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_InvalidIssuer() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.withBasicAuth(getSpecificProxyServiceProperties().getOidc().getClientId(), getSpecificProxyServiceProperties().getOidc().getClientSecret())
				.withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_invalid-id-token-issuer.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: Error when validating id_token! Unexpected JWT issuer: https://invalid-issuer:9877");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_InvalidAudience() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.withBasicAuth(getSpecificProxyServiceProperties().getOidc().getClientId(), getSpecificProxyServiceProperties().getOidc().getClientSecret())
				.withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_invalid-id-token-audience.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: Error when validating id_token! Unexpected JWT audience: [invalidClientId]");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_InvalidIdTokenSignature() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_invalid-id-token-signature.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: Error when validating id_token! Signed JWT rejected: Invalid signature");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_ConnectionTimesOut() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.willReturn(aResponse()
						.withStatus(200)
						.withFixedDelay(1500)));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: IO error while accessing OIDC token endpoint! Read timed out");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_InvalidRequest() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.withBasicAuth(getSpecificProxyServiceProperties().getOidc().getClientId(), getSpecificProxyServiceProperties().getOidc().getClientSecret())
				.withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
				.willReturn(aResponse()
						.withStatus(400)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_invalid-request.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: OIDC token request returned an error! invalid_request");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_MissingRequiredClaim_AttributeDateOfBirth() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.withBasicAuth(getSpecificProxyServiceProperties().getOidc().getClientId(), getSpecificProxyServiceProperties().getOidc().getClientSecret())
				.withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_missing_required_claim.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: Failed to read attribute (DateOfBirth) value from ID-token with jsonpath ($.profile_attributes.date_of_birth). Please check your configuration");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_MissingRequiredClaim_Amr() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.withBasicAuth(getSpecificProxyServiceProperties().getOidc().getClientId(), getSpecificProxyServiceProperties().getOidc().getClientSecret())
				.withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_missing_required_claim_amr.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: Missing required claim 'amr' in OIDC ID-token");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_InvalidAmr() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.withBasicAuth(getSpecificProxyServiceProperties().getOidc().getClientId(), getSpecificProxyServiceProperties().getOidc().getClientSecret())
				.withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_invalid_id-token_claim_value_amr.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: The amr claim returned in the OIDC ID-token response is not allowed by the configuration. amr = '[banklink]', allowed amr values by the configuration = '[idcard, mID]'");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void internalServerErrorWhenIdpError_TokenErrorResponse_InvalidClaimValueSub() throws Exception {

		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.withBasicAuth(getSpecificProxyServiceProperties().getOidc().getClientId(), getSpecificProxyServiceProperties().getOidc().getClientSecret())
				.withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/token-response-nok_invalid_id-token_claim_value_sub.json")));

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("error", equalTo("Internal Server Error"))
			.body("errors", nullValue())
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Something went wrong internally. Please consult server logs for further details."));

		assertErrorIsLogged("Server encountered an unexpected error: Attribute 'PersonIdentifier' with value 'XX60001019906' does not match the expected format ^EE(?<attributeValue>[\\d]{11,11})$");
		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void redirectToEidasnodeWithErrorWhen_IdpReturnsError_UserCancel() throws Exception {

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("error", "user_cancel")
			.param("state", mapEntry.getKey())
			.config(RestAssured.config().redirect(redirectConfig().followRedirects(false)))
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(302)
			.header(HttpHeaders.LOCATION, startsWith("https://ee-eidas-proxy:8083/EidasNode/SpecificProxyServiceResponse" +
					"?token=c3BlY2lmaWNDb21t"));

		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertResponseCommunicationCacheContainsUserCancelResponse("User canceled the authentication process", mapEntry.getValue().getLightRequest().getId());
	}

	Map.Entry<String, CorrelatedRequestsHolder> addMockRequestToPendingIdpRequestCommunicationCache() throws MalformedURLException {
		return addMockRequestToPendingIdpRequestCommunicationCache(createDefaultLightRequest());
	}

	Map.Entry<String, CorrelatedRequestsHolder> addMockRequestToPendingIdpRequestCommunicationCache(ILightRequest lightRequest) throws MalformedURLException {
		String stateParameterValue = UUID.randomUUID().toString();
		CorrelatedRequestsHolder requestsHolder = new CorrelatedRequestsHolder(lightRequest, Collections.singletonMap(stateParameterValue, new URL("http://oidAuthenticationRequest")));
		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = new AbstractMap.SimpleEntry<String, CorrelatedRequestsHolder>(stateParameterValue, requestsHolder);
		getIdpRequestCommunicationCache().put(stateParameterValue, requestsHolder);
		return mapEntry;
	}

	void createMockOidcServerResponse_successfulAuthentication(String code, String responseFile) throws UnsupportedEncodingException {
		mockOidcServer.stubFor(post(urlEqualTo("/oidc/token"))
				.withBasicAuth(
						getSpecificProxyServiceProperties().getOidc().getClientId(),
						getSpecificProxyServiceProperties().getOidc().getClientSecret())
				.withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
				.withRequestBody(containing("grant_type=authorization_code&code=" + code
						+ "&redirect_uri=" + URLEncoder.encode(getSpecificProxyServiceProperties().getOidc().getRedirectUri(), StandardCharsets.UTF_8)))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile(responseFile)));
	}
}



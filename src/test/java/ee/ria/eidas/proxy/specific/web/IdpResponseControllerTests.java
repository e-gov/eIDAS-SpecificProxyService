package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.storage.StoredMSProxyServiceRequestCorrelationMap;
import ee.ria.eidas.proxy.specific.storage.StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder;
import io.restassured.RestAssured;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.cache.Cache;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.*;
import static ee.ria.eidas.proxy.specific.web.IdpResponseController.ENDPOINT_IDP_RESPONSE;
import static ee.ria.eidas.proxy.specific.web.ProxyServiceRequestController.ENDPOINT_PROXY_SERVICE_REQUEST;
import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class IdpResponseControllerTests extends ControllerTest {

	@Value("${lightToken.proxyservice.response.issuer.name}")
	private String lightTokenResponseIssuerName;

	@Test
	void badRequestWhenMissingRequiredParam_state() {
		given()
			.param("sas", "asas")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("message", equalTo("Bad request"))
			.body("errors", hasSize(1))
			.body("errors", hasItem("Parameter state: must not be empty"));
	}

	@Test
	void badRequestWhenDuplicateParams_state() {
		given()
			.param("state", ".,.")
			.param("state", "...")
			.param("state", "...")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("message", equalTo("Bad request"))
			.body("errors", hasSize(1))
			.body("errors", hasItem("Parameter state: using multiple instances of parameter is not allowed"));
	}

	@Test
	void badRequestWhenMissingRequiredParam_missingBothCodeAndError() {
		given()
			.param("state", "...")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("message", equalTo("Bad request"))
			.body("errors", hasSize(1))
			.body("errors", hasItem("Either error or code parameter is required"));
	}

	@Test
	void badRequestWhenHavingBothCodeAndErrorParameters() {
		given()
			.param("state", "...")
			.param("code", "...")
			.param("error", "...")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("message", equalTo("Bad request"))
			.body("errors", hasSize(1))
			.body("errors", hasItem("Either error or code parameter can be present in a callback request. Both code and error parameters found"));
	}

	@Test
	void badRequestWhenInvalidOrNonexistingState_successResponse() {
		given()
			.param("state", "invalidState")
			.param("code", "1234567890abcdefghij")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("message", equalTo("Bad request"))
			.body("errors", hasSize(1))
			.body("errors", hasItem("Invalid state"));
	}

	@Test
	void badRequestWhenInvalidOrNonxistingState_errorResponse() {
		given()
			.param("state", "invalidState")
			.param("error", "user_cancel")
			.param("error_description", "User canceled")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("message", equalTo("Bad request"))
			.body("errors", hasSize(1))
			.body("errors", hasItem("Invalid state"));
	}

	@Test
	void invalidMethodsNotAllowed() {
		assertHttpMethodsNotAllowed( ENDPOINT_IDP_RESPONSE, "POST", "PUT", "DELETE", "CONNECT", "OPTIONS", "PATCH", "CUSTOM", "HEAD", "TRACE");
	}

	@Test
	void internalServerErrorWhenIdpError_InvalidRequest() throws Exception {

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToCommunicationCache();

		given()
			.param("error", "invalid_request")
			.param("state", mapEntry.getKey())
			.config(RestAssured.config().redirect(redirectConfig().followRedirects(false)))
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(500)
			.body("message", equalTo("Internal server error"))
			.body("errors", hasSize(1))
			.body("errors", hasItem("Something went wrong internally. Please consult server logs for further details."));

		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void redirectToEidasnodeWithErrorWhenIdpReturnsError_UserCancel() throws Exception {

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToCommunicationCache();

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

		assertResponseCommunicationCacheContainsUserCancelResponse(mapEntry);
	}

	private void assertResponseCommunicationCacheContainsUserCancelResponse(Map.Entry<String, CorrelatedRequestsHolder> mapEntry) throws SAXException, IOException, ParserConfigurationException {

		// verify expected Lightresponse in communication cache
		List<Cache.Entry<String, String>> list = getListFromIterator(getEidasNodeResponseCommunicationCache().iterator());
		assertEquals(1, list.size());
		assertThat(list.get(0).getKey(), matchesPattern(UUID_REGEX));

		Element responseXml = getXmlDocument(list.get(0).getValue());
		assertThat(responseXml, hasXPath("/lightResponse/id", matchesPattern(UUID_REGEX)));
		assertThat(responseXml, hasXPath("/lightResponse/inResponseToId", equalTo(mapEntry.getValue().getILightRequest().getId())));
		assertThat(responseXml, hasXPath("/lightResponse/issuer", equalTo(lightTokenResponseIssuerName)));
		assertThat(responseXml, hasXPath("/lightResponse/status/failure", equalTo("true")));
		assertThat(responseXml, hasXPath("/lightResponse/status/statusMessage", equalTo("User canceled the authentication process")));
		assertThat(responseXml, hasXPath("/lightResponse/status/subStatusCode", equalTo("urn:oasis:names:tc:SAML:2.0:status:RequestDenied")));
	}

	Map.Entry<String, CorrelatedRequestsHolder> addMockRequestToCommunicationCache() throws URISyntaxException {
		String stateParameterValue = UUID.randomUUID().toString();
		CorrelatedRequestsHolder requestsHolder = new CorrelatedRequestsHolder(createDefaultLightRequest(), Collections.singletonMap(stateParameterValue, new URI("http://oidAuthenticationRequest")));
		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = new AbstractMap.SimpleEntry<String, CorrelatedRequestsHolder>(stateParameterValue, requestsHolder);
		getEidasNodeRequestCommunicationCache().put(stateParameterValue, requestsHolder);
		return mapEntry;
	}
}



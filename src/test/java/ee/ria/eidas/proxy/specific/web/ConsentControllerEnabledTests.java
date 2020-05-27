package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.w3c.dom.Element;

import javax.cache.Cache;
import java.util.List;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.*;
import static ee.ria.eidas.proxy.specific.web.ConsentController.ENDPOINT_USER_CONSENT;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest( webEnvironment = RANDOM_PORT )
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = ConsentControllerEnabledTests.TestContextInitializer.class )
class ConsentControllerEnabledTests extends ControllerTest {

	@Test
	void methodNotAllowedWhenInvalidHttpMethod() {
		assertHttpMethodsNotAllowed(ENDPOINT_USER_CONSENT, "PUT", "DELETE", "CONNECT", "OPTIONS", "PATCH", "CUSTOM", "HEAD", "TRACE");
	}

	@Test
	void badRequestWhenMissingRequiredParameters_token() {
		given()
			.param("invalidParameter", "invalidParameterValue")
		.when()
			.get(ENDPOINT_USER_CONSENT)
		.then()
			.assertThat()
			.statusCode(400)
			.body("message", equalTo("Bad request"))
			.body("errors", hasSize(1))
			.body("errors", hasItem("Parameter token: must not be null"));

		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void badRequestWhenInvalidParameterValue_invalidCharacters() {
		given()
			.param(EidasParameterKeys.TOKEN.toString(), "'@´<>?")
		.when()
			.get(ENDPOINT_USER_CONSENT)
		.then()
			.assertThat()
			.statusCode(400)
			.body("message", equalTo("Bad request"))
			.body("errors", hasSize(1))
			.body("errors", hasItem("Parameter token[0]: only base64 characters allowed"));

		assertResponseCommunicationCacheIsEmpty();
	}

	@Test
	void badRequestWhenInvalidParameterValue_invalidLightTokenSignature() {
		given()
			.param(EidasParameterKeys.TOKEN.toString(), "YXNkZmZzZGZzZGZkc2ZzZmQ")
		.when()
			.get(ENDPOINT_USER_CONSENT)
		.then()
			.assertThat()
			.statusCode(400)
			.body("message", equalTo("Bad request"))
			.body("errors", hasSize(1))
			.body("errors", hasItem("Invalid token"));

		assertResponseCommunicationCacheIsEmpty();
	}


	@Test
	void redirectToEidasNodeWhenValidUserCancel() throws Exception {
		ILightResponse defaultLightResponse = createDefaultLightResponse();
		String mockBinaryLightToken = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(
				getSpecificProxyServiceCommunication().putPendingLightResponse(defaultLightResponse));

		given()
			.param("token", mockBinaryLightToken)
			.param("cancel", "true")
		.when()
			.get(ENDPOINT_USER_CONSENT)
		.then()
			.assertThat()
			.statusCode(302)
			.header(HttpHeaders.LOCATION, startsWith("https://ee-eidas-proxy:8083/EidasNode/SpecificProxyServiceResponse?token=c3BlY2lmaWNDb"));

		assertResponseCommunicationCacheContainsUserCancelResponse("User canceled the authentication process", defaultLightResponse.getInResponseToId());
	}



	@Test
	void redirectToEidasNodeWhenValidUserAccept() throws Exception {
		ILightResponse defaultLightResponse = createDefaultLightResponse();
		String mockBinaryLightToken = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(
				getSpecificProxyServiceCommunication().putPendingLightResponse(
						defaultLightResponse));

		given()
			.param("token", mockBinaryLightToken)
		.when()
			.get(ENDPOINT_USER_CONSENT)
		.then()
			.assertThat()
			.statusCode(302)
			.header(HttpHeaders.LOCATION, startsWith("https://ee-eidas-proxy:8083/EidasNode/SpecificProxyServiceResponse?token=c3BlY2lmaWNDb"));


		List<Cache.Entry<String, String>> list = getListFromIterator(getEidasNodeResponseCommunicationCache().iterator());
		assertEquals(1, list.size());
		assertThat(list.get(0).getKey(), matchesPattern(UUID_REGEX));

		Element responseXml = getXmlDocument(list.get(0).getValue());
		assertThat(responseXml, hasXPath("/lightResponse/id", equalTo(defaultLightResponse.getId())));
		assertThat(responseXml, hasXPath("/lightResponse/inResponseToId", equalTo(defaultLightResponse.getInResponseToId())));
		assertThat(responseXml, hasXPath("/lightResponse/relayState", equalTo(defaultLightResponse.getRelayState())));
		assertThat(responseXml, hasXPath("/lightResponse/levelOfAssurance", equalTo(defaultLightResponse.getLevelOfAssurance())));
		assertThat(responseXml, hasXPath("/lightResponse/issuer", equalTo(defaultLightResponse.getIssuer())));
		assertThat(responseXml, hasXPath("/lightResponse/subject", equalTo(defaultLightResponse.getSubject())));
		assertThat(responseXml, hasXPath("/lightResponse/subjectNameIdFormat", equalTo(defaultLightResponse.getSubjectNameIdFormat())));
		assertThat(responseXml, hasXPath("/lightResponse/ipAddress", equalTo(defaultLightResponse.getIPAddress())));
		assertThat(responseXml, hasXPath("/lightResponse/status/statusCode", equalTo("urn:oasis:names:tc:SAML:2.0:status:Success")));
		assertThat(responseXml, hasXPath("/lightResponse/status/failure", equalTo("false")));
		assertThat(responseXml, hasXPath("count(/lightResponse/attributes/attribute)", equalTo("4")));
		assertThat(responseXml, hasXPath("/lightResponse/attributes/attribute[definition = 'http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier']/value", equalTo("EE60001019906")));
		assertThat(responseXml, hasXPath("/lightResponse/attributes/attribute[definition = 'http://eidas.europa.eu/attributes/naturalperson/DateOfBirth']/value", equalTo("2000-01-01")));
		assertThat(responseXml, hasXPath("/lightResponse/attributes/attribute[definition = 'http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName']/value", equalTo("MARY ÄNN")));
		assertThat(responseXml, hasXPath("/lightResponse/attributes/attribute[definition = 'http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName']/value", equalTo("O’CONNEŽ-ŠUSLIK TESTNUMBER")));
	}
}



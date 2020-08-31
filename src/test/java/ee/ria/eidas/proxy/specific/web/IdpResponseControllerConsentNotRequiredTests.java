package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication.CorrelatedRequestsHolder;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.impl.LightRequest;
import eu.eidas.auth.commons.protocol.impl.SamlNameIdFormat;
import io.restassured.RestAssured;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.cache.Cache;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.*;
import static ee.ria.eidas.proxy.specific.web.IdpResponseController.ENDPOINT_IDP_RESPONSE;
import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
		webEnvironment = RANDOM_PORT,
		properties = {"eidas.proxy.ask-consent=false"}
)
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = IdpResponseControllerConsentNotRequiredTests.TestContextInitializer.class )
class IdpResponseControllerConsentNotRequiredTests extends IdpResponseControllerTests {

	@Test
	void redirectToEidasnodeWhenValidResponseAndConsentNotRequired() throws Exception {

		String code = UUID.randomUUID().toString();
		createMockOidcServerResponse_successfulAuthentication(code);
		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		given()
			.param("code", code)
			.param("state", mapEntry.getKey())
			.param("unknown-parameter-that-should-be-ignored", " * ? / \\ | < > , . ( ) [ ] { } ; : ‘ @ # $ % ^ &´`?0àáâãäåçèéêëìíîðñòôõöö")
			.config(RestAssured.config().redirect(redirectConfig().followRedirects(false)))
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(302)
			.header(HttpHeaders.LOCATION, startsWith("https://ee-eidas-proxy:8083/EidasNode/SpecificProxyServiceResponse" +
					"?token=c3BlY2lmaWNDb21t"));

		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertWarningIsLogged(SpecificProxyService.class,
				"Ignoring optional attribute BirthName - no mapping configured to extract it's corresponding value from id-token",
				"Ignoring optional attribute Gender - no mapping configured to extract it's corresponding value from id-token",
				"Ignoring optional attribute PlaceOfBirth - no mapping configured to extract it's corresponding value from id-token",
				"Ignoring optional attribute CurrentAddress - no mapping configured to extract it's corresponding value from id-token");
		assertResponse(mapEntry);
	}

	@Test
	void redirectToEidasnodeWhenValidResponseAndConsentNotRequired_NameIdSpecified() throws Exception {

		String code = UUID.randomUUID().toString();
		createMockOidcServerResponse_successfulAuthentication(code);
		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache(
				createDefaultLightRequest(SamlNameIdFormat.PERSISTENT.getNameIdFormat()));

		given()
			.param("code", code)
			.param("state", mapEntry.getKey())
			.param("unknown-parameter-that-should-be-ignored", " * ? / \\ | < > , . ( ) [ ] { } ; : ‘ @ # $ % ^ &´`?0àáâãäåçèéêëìíîðñòôõöö")
			.config(RestAssured.config().redirect(redirectConfig().followRedirects(false)))
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(302)
			.header(HttpHeaders.LOCATION, startsWith("https://ee-eidas-proxy:8083/EidasNode/SpecificProxyServiceResponse" +
					"?token=c3BlY2lmaWNDb21t"));

		assertPendingIdpRequestCommunicationCacheIsEmpty();
		assertWarningIsLogged(SpecificProxyService.class,
				"Ignoring optional attribute BirthName - no mapping configured to extract it's corresponding value from id-token",
				"Ignoring optional attribute Gender - no mapping configured to extract it's corresponding value from id-token",
				"Ignoring optional attribute PlaceOfBirth - no mapping configured to extract it's corresponding value from id-token",
				"Ignoring optional attribute CurrentAddress - no mapping configured to extract it's corresponding value from id-token");
		assertResponse(mapEntry);
	}

	private void assertResponse(Map.Entry<String, CorrelatedRequestsHolder> mapEntry) throws SAXException, IOException, ParserConfigurationException {
		List<Cache.Entry<String, String>> list = getListFromIterator(getEidasNodeResponseCommunicationCache().iterator());
		assertEquals(1, list.size());
		assertThat(list.get(0).getKey(), matchesPattern(UUID_REGEX));

		Element responseXml = getXmlDocument(list.get(0).getValue());
		assertThat(responseXml, hasXPath("/lightResponse/id", equalTo("7d02cecd-6a63-4124-97fa-74999817fb08"))); // jti claim value in id-token - token-response-ok.json
		ILightRequest originalLightRequest = mapEntry.getValue().getLightRequest();
		assertThat(responseXml, hasXPath("/lightResponse/inResponseToId", equalTo(originalLightRequest.getId())));
		assertThat(responseXml, hasXPath("/lightResponse/relayState", equalTo(originalLightRequest.getRelayState())));
		assertThat(responseXml, hasXPath("/lightResponse/levelOfAssurance", equalTo(originalLightRequest.getLevelOfAssurance().toString())));
		assertThat(responseXml, hasXPath("/lightResponse/issuer", equalTo("https://localhost:9877"))); // iss claim value in id-token - token-response-ok.json
		assertThat(responseXml, hasXPath("/lightResponse/subject", equalTo("EE60001019906"))); // sub claim value in id-token - token-response-ok.json
		assertThat(responseXml, hasXPath("/lightResponse/ipAddress", matchesPattern(IP_REGEX)));
		assertThat(responseXml, hasXPath("/lightResponse/status/statusCode", equalTo("urn:oasis:names:tc:SAML:2.0:status:Success")));
		assertThat(responseXml, hasXPath("/lightResponse/status/failure", equalTo("false")));

		if (StringUtils.isNotEmpty(originalLightRequest.getNameIdFormat())) {
			assertThat(responseXml, hasXPath("/lightResponse/subjectNameIdFormat", equalTo(originalLightRequest.getNameIdFormat())));
		} else {
			assertThat(responseXml, hasXPath("/lightResponse/subjectNameIdFormat", equalTo("urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified")));
		}

		assertThat(responseXml, hasXPath("count(/lightResponse/attributes/attribute)", equalTo("4")));
		assertThat(responseXml, hasXPath("/lightResponse/attributes/attribute[definition = 'http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier']/value", equalTo("60001019906"))); // id code from sub claim value in id-token - token-response-ok.json
		assertThat(responseXml, hasXPath("/lightResponse/attributes/attribute[definition = 'http://eidas.europa.eu/attributes/naturalperson/DateOfBirth']/value", equalTo("2000-01-01"))); // profile_attributes.date_of_birth claim value in id-token - token-response-ok.json
		assertThat(responseXml, hasXPath("/lightResponse/attributes/attribute[definition = 'http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName']/value", equalTo("MARY ÄNN"))); // profile_attributes.given_name claim value in id-token - token-response-ok.json
		assertThat(responseXml, hasXPath("/lightResponse/attributes/attribute[definition = 'http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName']/value", equalTo("O’CONNEŽ-ŠUSLIK TESTNUMBER"))); // profile_attributes.family_name claim value in id-token - token-response-ok.json
	}
}



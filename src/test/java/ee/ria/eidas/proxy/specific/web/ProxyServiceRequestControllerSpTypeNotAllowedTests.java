package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.impl.LightRequest;
import eu.eidas.auth.commons.protocol.eidas.LevelOfAssurance;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.w3c.dom.Element;

import javax.cache.Cache;
import java.util.List;
import java.util.UUID;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.*;
import static ee.ria.eidas.proxy.specific.web.ProxyServiceRequestController.ENDPOINT_PROXY_SERVICE_REQUEST;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
		webEnvironment = RANDOM_PORT,
		properties = {"eidas.proxy.supported-sp-types=public"} )
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = ProxyServiceRequestControllerSpTypeNotAllowedTests.TestContextInitializer.class )
class ProxyServiceRequestControllerSpTypeNotAllowedTests extends ControllerTest {

	@Value("${lightToken.proxyservice.response.issuer.name}")
	private String lightTokenResponseIssuerName;

	@Test
	void redirectToEidasnodeWhenSpTypeNotAllowed() throws Exception {
		ILightRequest mockLightRequest = LightRequest.builder()
				.id(UUID.randomUUID().toString())
				.citizenCountryCode("CA")
				.issuer("issuerName")
				.spType("private")
				.levelOfAssurance(LevelOfAssurance.HIGH.stringValue()).build();

		BinaryLightToken mockBinaryLightToken = getSpecificProxyService().putRequest(mockLightRequest);

		given()
			.param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
		.when()
			.get(ENDPOINT_PROXY_SERVICE_REQUEST)
		.then()
			.assertThat()
			.statusCode(302)
			.header(HttpHeaders.LOCATION, startsWith("https://ee-eidas-proxy:8083/EidasNode/SpecificProxyServiceResponse?token=c3BlY2lmaW"));

		// assert Lightresponse in communication cache
		List<Cache.Entry<String, String>> list = getListFromIterator(getEidasNodeResponseCommunicationCache().iterator());
		assertEquals(1, list.size());
		assertThat(list.get(0).getKey(), matchesPattern(UUID_REGEX));

		Element responseXml = getXmlDocument(list.get(0).getValue());
		assertThat(responseXml, hasXPath("/lightResponse/id", matchesPattern(UUID_REGEX)));
		assertThat(responseXml, hasXPath("/lightResponse/inResponseToId", equalTo(mockLightRequest.getId())));
		assertThat(responseXml, hasXPath("/lightResponse/issuer", equalTo(lightTokenResponseIssuerName)));
		assertThat(responseXml, hasXPath("/lightResponse/status/failure", equalTo("true")));
		assertThat(responseXml, hasXPath("/lightResponse/status/statusMessage", equalTo("Service provider type not supported. Allowed types: [public]")));
		assertThat(responseXml, hasXPath("/lightResponse/status/subStatusCode", equalTo("urn:oasis:names:tc:SAML:2.0:status:RequestDenied")));
	}

}



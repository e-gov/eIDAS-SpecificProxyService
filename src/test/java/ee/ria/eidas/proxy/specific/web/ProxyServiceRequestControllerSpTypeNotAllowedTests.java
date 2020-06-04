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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.UUID;

import static ee.ria.eidas.proxy.specific.web.ProxyServiceRequestController.ENDPOINT_PROXY_SERVICE_REQUEST;
import static io.restassured.RestAssured.given;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
		webEnvironment = RANDOM_PORT,
		properties = {"eidas.proxy.supported-sp-types=public"} )
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = ProxyServiceRequestControllerSpTypeNotAllowedTests.TestContextInitializer.class )
class ProxyServiceRequestControllerSpTypeNotAllowedTests extends ControllerTest {

	@Test
	void redirectToEidasnodeWhenSpTypeNotAllowed() throws Exception {
		ILightRequest mockLightRequest = LightRequest.builder()
				.id(UUID.randomUUID().toString())
				.citizenCountryCode("CA")
				.issuer("issuerName")
				.spType("private")
				.levelOfAssurance(LevelOfAssurance.HIGH.stringValue()).build();

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
}



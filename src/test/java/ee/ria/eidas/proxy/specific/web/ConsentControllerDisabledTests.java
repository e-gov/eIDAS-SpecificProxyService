package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.createDefaultLightResponse;
import static ee.ria.eidas.proxy.specific.web.ConsentController.ENDPOINT_USER_CONSENT;
import static io.restassured.RestAssured.given;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
		webEnvironment = RANDOM_PORT,
		properties = {"eidas.proxy.ask-consent=false"} )
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = ConsentControllerDisabledTests.TestContextInitializer.class )
class ConsentControllerDisabledTests extends ControllerTest {

	@Test
	void consentEndpointNotAccessible() throws Exception {
		String mockBinaryLightToken = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(getSpecificProxyServiceCommunication().putPendingLightResponse(createDefaultLightResponse()));

		given()
			.param("binaryLightToken", mockBinaryLightToken)
		.when()
			.get(ENDPOINT_USER_CONSENT)
		.then()
			.assertThat()
			.statusCode(404);
	}
}



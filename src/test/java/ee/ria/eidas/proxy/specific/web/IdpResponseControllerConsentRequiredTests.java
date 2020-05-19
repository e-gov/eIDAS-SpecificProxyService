package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static ee.ria.eidas.proxy.specific.web.IdpResponseController.ENDPOINT_IDP_RESPONSE;
import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
		webEnvironment = RANDOM_PORT
)
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = IdpResponseControllerConsentRequiredTests.TestContextInitializer.class )
class IdpResponseControllerConsentRequiredTests extends IdpResponseControllerTests {

	@Test
	void validResponseWhenConsentRequired() throws Exception {

		String stateParameterValue = addMockRequestToCommunicationCache();

		Response response = given()
			.param("code", "123...")
			.param("state", stateParameterValue)
			.config(RestAssured.config().redirect(redirectConfig().followRedirects(false)))
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat().statusCode(200)
			.extract().response();

		// TODO
		//assertThat( response.body().htmlPath().getString("html.body.span.input.@value")).contains("dasdasd");
	}
}



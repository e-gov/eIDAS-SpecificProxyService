package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.storage.StoredMSProxyServiceRequestCorrelationMap;
import ee.ria.eidas.proxy.specific.storage.StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.Map;

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

		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToCommunicationCache();

		Response response = given()
			.param("code", "123...")
			.param("state", mapEntry.getKey())
			.config(RestAssured.config().redirect(redirectConfig().followRedirects(false)))
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat().statusCode(200)
			.extract().response();

		// TODO assert HTML page content
		//assertThat( response.body().htmlPath().getString("html.body.span.input.@value")).contains("dasdasd");
	}
}



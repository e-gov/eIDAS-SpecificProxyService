package ee.ria.eidas.proxy.specific.web;

import com.nimbusds.oauth2.sdk.util.URLUtils;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.net.URL;
import java.util.List;
import java.util.Map;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.createDefaultLightRequest;
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

		URL url = getSpecificProxyService().translateNodeRequest(createDefaultLightRequest(), null);
		Map<String, List<String>> parameters = URLUtils.parseParameters(url.getQuery());

		Response response = given()
			.param("code", "123...")
			.param("state", parameters.get("state").get(0))
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



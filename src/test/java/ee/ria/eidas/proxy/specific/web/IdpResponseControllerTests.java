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
import static org.hamcrest.CoreMatchers.equalTo;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

abstract class IdpResponseControllerTests extends ControllerTest {

	@Test
	void missingRequiredParam() {
		given()
			.param("sas", "asas")
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(400)
			.body("status", equalTo(400))
			.body("error", equalTo("Bad Request"))
			.body("message", equalTo("Required String parameter 'state' is not present"));
	}
}



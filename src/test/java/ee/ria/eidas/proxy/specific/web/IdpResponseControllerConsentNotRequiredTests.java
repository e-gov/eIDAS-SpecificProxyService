package ee.ria.eidas.proxy.specific.web;

import com.nimbusds.oauth2.sdk.util.URLUtils;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import io.restassured.RestAssured;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;

import java.net.URL;
import java.util.List;
import java.util.Map;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.createDefaultLightRequest;
import static ee.ria.eidas.proxy.specific.web.IdpResponseController.ENDPOINT_IDP_RESPONSE;
import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
		webEnvironment = RANDOM_PORT,
		properties = {"eidas.proxy.ask-consent=false"}
)
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = IdpResponseControllerConsentNotRequiredTests.TestContextInitializer.class )
class IdpResponseControllerConsentNotRequiredTests extends IdpResponseControllerTests {

	@Test
	void validResponseWhenConsentNotRequired() throws Exception {

		URL url = getSpecificProxyService().translateNodeRequest(createDefaultLightRequest(), null);
		Map<String, List<String>> parameters = URLUtils.parseParameters(url.getQuery());

		given()
			.param("code", "123...")
			.param("state", parameters.get("state").get(0))
			.config(RestAssured.config().redirect(redirectConfig().followRedirects(false)))
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
				.statusCode(302)
				.header(HttpHeaders.LOCATION, startsWith("https://ee-eidas-proxy:8083/EidasNode/SpecificProxyServiceResponse" +
						"?token=c3BlY2lmaWNDb21t"));
	}
}



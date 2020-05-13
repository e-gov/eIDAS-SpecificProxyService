package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import io.restassured.RestAssured;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.createDefaultLightRequest;
import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.createDefaultLightResponse;
import static ee.ria.eidas.proxy.specific.web.ConsentController.ENDPOINT_USER_CONSENT;
import static ee.ria.eidas.proxy.specific.web.ProxyServiceRequestController.ENDPOINT_PROXY_SERVICE_REQUEST;
import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest( webEnvironment = RANDOM_PORT )
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = ConsentControllerTests.TestContextInitializer.class )
class ConsentControllerTests extends ControllerTest {

	@Test
	void validLightTokenAndCancel() throws Exception {
		String mockBinaryLightToken = getSpecificProxyService().createStoreBinaryLightTokenResponseBase64(createDefaultLightResponse());

		given()
			.param("binaryLightToken", mockBinaryLightToken)
			.param("cancel", "true")
			.config(RestAssured.config().redirect(redirectConfig().followRedirects(false)))
		.when()
			.get(ENDPOINT_USER_CONSENT)
		.then()
			.assertThat()
			.statusCode(302)
			.header(HttpHeaders.LOCATION, startsWith("https://ee-eidas-proxy:8083/EidasNode/SpecificProxyServiceResponse?token=c3BlY2lmaWNDb"));

		// TODO assert cache content
	}



	@Test
	void validLightToken() throws Exception {
		String mockBinaryLightToken = getSpecificProxyService().createStoreBinaryLightTokenResponseBase64(createDefaultLightResponse());

		given()
			.param("binaryLightToken", mockBinaryLightToken)
			.config(RestAssured.config().redirect(redirectConfig().followRedirects(false)))
		.when()
			.get(ENDPOINT_USER_CONSENT)
		.then()
			.assertThat()
			.statusCode(302)
			.header(HttpHeaders.LOCATION, startsWith("https://ee-eidas-proxy:8083/EidasNode/SpecificProxyServiceResponse?token=c3BlY2lmaWNDb"));

		// TODO assert cache content
	}
}



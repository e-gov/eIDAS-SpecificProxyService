package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.storage.StoredMSProxyServiceRequestCorrelationMap;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.createDefaultLightRequest;
import static ee.ria.eidas.proxy.specific.web.IdpResponseController.ENDPOINT_IDP_RESPONSE;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

abstract class IdpResponseControllerTests extends ControllerTest {


	String addMockRequestToCommunicationCache() throws URISyntaxException {
		String stateParemeterValue = "state";
		getEidasNodeRequestCommunicationCache().put(stateParemeterValue, new StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder(createDefaultLightRequest(), Collections.singletonMap(stateParemeterValue, new URI("http://oidAuthenticationRequest"))));
		return stateParemeterValue;
	}

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



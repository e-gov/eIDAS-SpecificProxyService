package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.impl.LightRequest;
import eu.eidas.auth.commons.protocol.eidas.LevelOfAssurance;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import io.restassured.response.Response;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.UUID;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.*;
import static ee.ria.eidas.proxy.specific.web.ProxyServiceRequestController.ENDPOINT_PROXY_SERVICE_REQUEST;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
		webEnvironment = RANDOM_PORT,
		properties = {"eidas.proxy.legal-person-attributes-not-accepted=true"} )
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = ProxyServiceRequestControllerLegalPersonAttributeSupportDisabledTests.TestContextInitializer.class )
class ProxyServiceRequestControllerLegalPersonAttributeSupportDisabledTests extends ControllerTest {

	@Test
	void redirectToIdpWhenValidRequest_LegalPersonAllAttributes() throws Exception {
		ILightRequest mockLightRequest = createLightRequest(new ImmutableAttributeMap.Builder()
				.putAll(LEGAL_PERSON_MANDATORY_ATTRIBUTES).putAll(LEGAL_PERSON_OPTIONAL_ATTRIBUTES).build());
		BinaryLightToken mockBinaryLightToken = putRequest(mockLightRequest);

		given()
			.param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
		.when()
			.get(ENDPOINT_PROXY_SERVICE_REQUEST)
		.then()
			.assertThat()
			.statusCode(400)
			.body("error", equalTo("Bad Request"))
			.body("incidentNumber", notNullValue())
			.body("message", equalTo("Support for legal person attributes has been temporarily suspended"));

		assertResponseCommunicationCacheIsEmpty();
	}
}



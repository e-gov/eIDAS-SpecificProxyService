package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication.CorrelatedRequestsHolder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.Map;
import java.util.UUID;

import static ee.ria.eidas.proxy.specific.web.IdpResponseController.ENDPOINT_IDP_RESPONSE;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
		webEnvironment = RANDOM_PORT
)
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = IdpResponseControllerConsentRequiredTests.TestContextInitializer.class )
class IdpResponseControllerConsentRequiredTests extends IdpResponseControllerTests {

	@Test
	void returnHtmlErrorPageWhenConsentRequired() throws Exception {

		String code = UUID.randomUUID().toString();
		createMockOidcServerResponse_successfulAuthentication(code);
		Map.Entry<String, CorrelatedRequestsHolder> mapEntry = addMockRequestToPendingIdpRequestCommunicationCache();

		Response response = given()
			.param("code", code)
			.param("state", mapEntry.getKey())
		.when()
			.get(ENDPOINT_IDP_RESPONSE)
		.then()
			.assertThat()
			.statusCode(200)
			.contentType(ContentType.HTML)
			.extract().response();

		assertThat( response.body().htmlPath().getString("html.head.title")).contains("Consent");
		assertThat( response.body().htmlPath().getString("**.findAll { it.strong.@id == 'FirstName'}").trim()).contains("MARY ÄNN");
		assertThat( response.body().htmlPath().getString("**.findAll { it.strong.@id == 'FamilyName'}").trim()).contains("O’CONNEŽ-ŠUSLIK TESTNUMBER");
		assertThat( response.body().htmlPath().getString("**.findAll { it.strong.@id == 'DateOfBirth'}").trim()).contains("2000-01-01");
		assertThat( response.body().htmlPath().getString("**.findAll { it.strong.@id == 'PersonIdentifier'}").trim()).contains("60001019906");

		assertPendingIdpRequestCommunicationCacheIsEmpty();
	}
}



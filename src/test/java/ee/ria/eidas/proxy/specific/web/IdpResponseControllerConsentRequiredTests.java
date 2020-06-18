package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication.CorrelatedRequestsHolder;
import io.restassured.http.ContentType;
import io.restassured.path.xml.XmlPath;
import io.restassured.path.xml.config.XmlPathConfig;
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

		assertThat( response.body().htmlPath().getString("**.findAll { span -> span.@id == 'spId'}").trim()).isEqualTo("mock_sp_name");
		assertThat( response.body().htmlPath().getNode("**.findAll { span -> span.@id == 'LoA'}").value().trim()).isEqualTo("http://eidas.europa.eu/LoA/high");
		assertThat( response.body().htmlPath().getString("**.findAll { it.strong.@id == 'FirstName'}").trim()).isEqualTo("MARY ÄNN");
		assertThat( response.body().htmlPath().getString("**.findAll { it.strong.@id == 'FamilyName'}").trim()).isEqualTo("O’CONNEŽ-ŠUSLIK TESTNUMBER");
		assertThat( response.body().htmlPath().getString("**.findAll { it.strong.@id == 'DateOfBirth'}").trim()).isEqualTo("2000-01-01");
		assertThat( response.body().htmlPath().getString("**.findAll { it.strong.@id == 'PersonIdentifier'}").trim()).isEqualTo("60001019906");

		assertWarningIsLogged(SpecificProxyService.class.getCanonicalName(),
				"Ignoring optional attribute BirthName - no mapping configured to extract it's corresponding value from id-token",
				"Ignoring optional attribute Gender - no mapping configured to extract it's corresponding value from id-token",
				"Ignoring optional attribute PlaceOfBirth - no mapping configured to extract it's corresponding value from id-token",
				"Ignoring optional attribute CurrentAddress - no mapping configured to extract it's corresponding value from id-token");

		assertPendingIdpRequestCommunicationCacheIsEmpty();
	}
}



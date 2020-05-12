package ee.ria.eidas.proxy.specific.web;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.protocol.SpecificCommunicationService;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;


import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.createDefaultLightRequest;
import static ee.ria.eidas.proxy.specific.web.ProxyServiceRequestController.ENDPOINT_PROXY_SERVICE_REQUEST;
import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest( webEnvironment = RANDOM_PORT )
@ContextConfiguration( classes = SpecificProxyServiceConfiguration.class, initializers = ProxyServiceRequestControllerTests.TestContextInitializer.class )
class ProxyServiceRequestControllerTests {

	@LocalServerPort
	private int port;

	@Autowired
	@Qualifier("springManagedSpecificProxyserviceCommunicationService")
	private SpecificCommunicationService specificCommunicationService;

	private static WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(9877));

	@BeforeAll
	public static void setupAll() {
		configureRestAssured();
		configureMockOidcServer();
	}

	@Test
	@Order(1)
	void contextLoads() {
		assertNotNull(specificCommunicationService, "Should not be null!");
	}

	@Test
	void invalidParameterName() {
		given()
			.param("sas", "asas")
		.when()
			.get(ENDPOINT_PROXY_SERVICE_REQUEST)
		.then()
			.assertThat()
			.statusCode(400);
	}

	@Test
	void invalidParameterValue_invalidCharacters() {
		given()
			.param(EidasParameterKeys.TOKEN.toString(), "'@´<>?")
		.when()
			.get(ENDPOINT_PROXY_SERVICE_REQUEST)
		.then()
			.assertThat()
			.statusCode(400);
	}

	@Test
	void invalidLightToken() {
		given()
			.param(EidasParameterKeys.TOKEN.toString(), "YXNkZmZzZGZzZGZkc2ZzZmQ")
		.when()
			.get(ENDPOINT_PROXY_SERVICE_REQUEST)
		.then()
			.assertThat()
			.statusCode(400);
	}

	@Test
	void validLightToken() throws Exception {
		BinaryLightToken mockBinaryLightToken = specificCommunicationService.putRequest(createDefaultLightRequest());

		given()
			.param(EidasParameterKeys.TOKEN.toString(), BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken))
			.config(RestAssured.config().redirect(redirectConfig().followRedirects(false)))
		.when()
			.get(ENDPOINT_PROXY_SERVICE_REQUEST)
		.then()
			.assertThat()
			.statusCode(302)
			.header(HttpHeaders.LOCATION, startsWith("https://tara-mock/oidc/authorize?scope=openid%20idcard%20mid&response_type=code&client_id=openIdDemo&redirect_uri=http://localhost:9877/redirect&state="));
	}

	public static class TestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		@Override
		public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
			String currentDirectory = System.getProperty("user.dir");
			System.setProperty("SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/config/tomcat/specificProxyService");
			System.setProperty("EIDAS_CONFIG_REPOSITORY", currentDirectory + "/src/test/resources/config/tomcat");
		}
	}

	private static void configureMockOidcServer() {
		wireMockServer.start();

		wireMockServer.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json; charset=UTF-8")
						.withBodyFile("mock_responses/idp/openid-configuration.json")));
	}

	private static void configureRestAssured() {
		RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());

	}

	@BeforeEach
	public void start() {
		RestAssured.port = port;
	}

	@AfterAll
	public static void stop() {
		wireMockServer.stop();
	}
}



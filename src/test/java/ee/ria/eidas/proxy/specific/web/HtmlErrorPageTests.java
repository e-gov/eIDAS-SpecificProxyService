package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import io.restassured.http.ContentType;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static ee.ria.eidas.proxy.specific.web.ConsentController.ENDPOINT_USER_CONSENT;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"server.error.whitelabel.enabled=false"})
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = HtmlErrorPageTests.TestContextInitializer.class)
public class HtmlErrorPageTests extends ControllerTest {
    public static final String EXPECTED_ERROR_VALUE = "Bad Request";
    public static final String EXPECTED_MESSAGE_VALUE = "Validation failed for object='requestParameters'. Error count: 1";
    public static final String EXPECTED_ERRORS_VALUE = "Parameter 'token': must not be null";
    public static final String EXPECTED_INCIDENT_ID_VALUE_PATTERN = "Eidas Proxyservice incident number: [a-z,0-9]{32}";

    @Test
    void returnsHtmlWhen_AcceptContentTypeHtml() {
        ValidatableResponse validatableResponse = getValidatableResponse(ContentType.HTML);
        Response response = validatableResponse.extract().response();

        XmlPath htmlPath = response.htmlPath();
        assertEquals(htmlPath.getString("html.head.title"), "Eesti autentimisteenus");
        assertEquals("Secure authentication in e-Services of EU member states",
                htmlPath.getString("**.find { it.@id == 'subtitle' }"));
        assertEquals("resource/error/assets/eeidp-logo-et.png",
                htmlPath.getString("**.find { it.@id == 'eeidp-logo-et' }.@src"));
        assertEquals("resource/error/assets/cef-logo-en.svg",
                htmlPath.getString("**.find { it.@id == 'cef-logo-en' }.@src"));

        assertEquals(EXPECTED_ERROR_VALUE,
                htmlPath.getString("**.find { it.@id == 'error' }"));
        assertEquals(EXPECTED_MESSAGE_VALUE,
                htmlPath.getString("**.find { it.@id == 'message' }"));
        assertEquals(EXPECTED_ERRORS_VALUE,
                htmlPath.getString("**.find { it.@id == 'errors' }"));
        assertThat(htmlPath.getString("**.find { it.@id == 'incidentNumber' }"),
                matchesPattern(EXPECTED_INCIDENT_ID_VALUE_PATTERN));
    }

    @Test
    void returnsJsonWhen_AcceptContentTypeJson() {
        ValidatableResponse response = getValidatableResponse(ContentType.JSON);
        response.body("error", equalTo(EXPECTED_ERROR_VALUE))
                .body("errors", equalTo(EXPECTED_ERRORS_VALUE))
                .body("incidentNumber", matchesPattern("[a-z,0-9]{32}"))
                .body("message", equalTo(EXPECTED_MESSAGE_VALUE));
    }

    private ValidatableResponse getValidatableResponse(ContentType contentType) {
        return given()
                .accept(contentType)
                .param("sas", "asas")
                .when()
                .get(ENDPOINT_USER_CONSENT)
                .then()
                .assertThat()
                .contentType(contentType)
                .statusCode(400);
    }
}

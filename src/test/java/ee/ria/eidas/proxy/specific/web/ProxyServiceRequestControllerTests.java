package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import io.restassured.RestAssured;
import io.restassured.builder.ResponseSpecBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.*;
import static ee.ria.eidas.proxy.specific.web.ProxyServiceRequestController.ENDPOINT_PROXY_SERVICE_REQUEST;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = ProxyServiceRequestControllerTests.TestContextInitializer.class)
class ProxyServiceRequestControllerTests extends ControllerTest {

    @Test
    void methodNotAllowedWhenInvalidHttpMethod() {
        assertHttpMethodsNotAllowed(ENDPOINT_PROXY_SERVICE_REQUEST, "PUT", "DELETE", "OPTIONS", "PATCH", "CUSTOM", "HEAD", "TRACE");
    }

    @Test
    void ConnectHttpMethodRespondsWith501() {
        RestAssured.responseSpecification = new ResponseSpecBuilder().build();

        given()
                .when()
                .request("CONNECT", ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(501);
    }

    @Test
    void badRequestWhenMissingRequiredParameters_token() {
        given()
                .param("invalidParameter", "invalidParameterValue")
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(400)
                .body("error", equalTo("Bad Request"))
                .body("errors", equalTo("Parameter 'token': must not be null"))
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Validation failed for object='requestParameters'. Error count: 1"));

        assertResponseCommunicationCacheIsEmpty();
    }

    @Test
    void badRequestWhenInvalidParameterValue_invalidCharacters() {
        given()
                .param(EidasParameterKeys.TOKEN.toString(), "'@´<>?")
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(400)
                .body("error", equalTo("Bad Request"))
                .body("errors", equalTo("Parameter 'token[0]': only base64 characters allowed"))
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Validation failed for object='requestParameters'. Error count: 1"));

        assertResponseCommunicationCacheIsEmpty();
    }

    @Test
    void badRequestWhenInvalidParameterValue_invalidLightTokenSignature() {
        given()
                .param(EidasParameterKeys.TOKEN.toString(), "YXNkZmZzZGZzZGZkc2ZzZmQ")
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(400)
                .body("error", equalTo("Bad Request"))
                .body("errors", nullValue())
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Invalid token"));

        assertResponseCommunicationCacheIsEmpty();
    }

    @Test
    void badRequestWhenInvalidParameterValue_multipleParameterValuesNotSupported() throws Exception {
        BinaryLightToken mockBinaryLightToken = putRequest(createDefaultLightRequest());
        String tokenBase64 = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken);

        given()
                .param(EidasParameterKeys.TOKEN.toString(), tokenBase64)
                .param(EidasParameterKeys.TOKEN.toString(), tokenBase64)
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(400)
                .body("error", equalTo("Bad Request"))
                .body("errors", equalTo("Parameter 'token': using multiple instances of parameter is not allowed"))
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Validation failed for object='requestParameters'. Error count: 1"));

        assertResponseCommunicationCacheIsEmpty();
    }

    @Test
    void badRequestWhenInvalidLightToken_invalidValueOrExpired() throws Exception {
        ILightRequest defaultLightRequest = createDefaultLightRequest();
        BinaryLightToken mockBinaryLightToken = putRequest(defaultLightRequest);
        String tokenBase64 = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(mockBinaryLightToken);
        getEidasNodeRequestCommunicationCache().getAndRemove(mockBinaryLightToken.getToken().getId());

        given()
                .param(EidasParameterKeys.TOKEN.toString(), tokenBase64)
                .when()
                .get(ENDPOINT_PROXY_SERVICE_REQUEST)
                .then()
                .assertThat()
                .statusCode(400)
                .body("error", equalTo("Bad Request"))
                .body("errors", nullValue())
                .body("incidentNumber", notNullValue())
                .body("message", equalTo("Invalid token"));

        assertResponseCommunicationCacheIsEmpty();
    }

    @Test
    void badRequestWhenInvalidLightToken_legalPersonAndNaturalPersonAttributesRequested() throws Exception {
        ILightRequest mockLightRequest = createLightRequest(new ImmutableAttributeMap.Builder()
                .putAll(LEGAL_PERSON_MANDATORY_ATTRIBUTES).putAll(NATURAL_PERSON_ALL_ATTRIBUTES).build());
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
                .body("message", equalTo("Request may not contain both legal person and natural person attributes"));

        assertResponseCommunicationCacheIsEmpty();
    }
}





package ee.ria.eidas.proxy.specific.web;

import com.nimbusds.oauth2.sdk.util.URLUtils;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.attribute.PersonType;
import eu.eidas.auth.commons.attribute.impl.LiteralStringAttributeValueMarshaller;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.protocol.eidas.spec.NaturalPersonSpec;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import io.restassured.response.Response;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import javax.cache.Cache;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.*;
import static ee.ria.eidas.proxy.specific.web.ProxyServiceRequestController.ENDPOINT_PROXY_SERVICE_REQUEST;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = ProxyServiceRequestControllerTests.TestContextInitializer.class)
class ProxyServiceRequestControllerTests extends ControllerTest {

    @Test
    void methodNotAllowedWhenInvalidHttpMethod() {
        assertHttpMethodsNotAllowed(ENDPOINT_PROXY_SERVICE_REQUEST, "PUT", "DELETE", "CONNECT", "OPTIONS", "PATCH", "CUSTOM", "HEAD", "TRACE");
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
}





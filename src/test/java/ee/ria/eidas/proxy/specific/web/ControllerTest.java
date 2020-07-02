package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.SpecificProxyTest;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.cache.Cache;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public abstract class ControllerTest extends SpecificProxyTest {

    @Value("${lightToken.proxyservice.request.issuer.name}")
    private String lightTokenRequestIssuerName;

    @Value("${lightToken.proxyservice.request.secret}")
    private String lightTokenRequestSecret;

    @Value("${lightToken.proxyservice.request.algorithm}")
    private String lightTokenRequestAlgorithm;

    @Value("${lightToken.proxyservice.response.issuer.name}")
    private String lightTokenResponseIssuerName;

    @Autowired
    @Getter
    private SpecificProxyServiceCommunication specificProxyServiceCommunication;

    @BeforeEach
    public void startTest() {
        clearMockOidcServerMappings();
        clearCommunicationCache();
    }

    BinaryLightToken putRequest(final ILightRequest iLightRequest) throws SpecificCommunicationException {
        final BinaryLightToken binaryLightToken = BinaryLightTokenHelper.createBinaryLightToken(
                lightTokenRequestIssuerName,
                lightTokenRequestSecret,
                lightTokenRequestAlgorithm);
        final String tokenId = binaryLightToken.getToken().getId();

        getEidasNodeRequestCommunicationCache().put(tokenId, codec.marshall(iLightRequest));
        return binaryLightToken;
    }

    void assertRequestCommunicationCacheIsEmpty() {
        List<Cache.Entry<String, String>> list = getListFromIterator(getEidasNodeRequestCommunicationCache().iterator());
        assertEquals(0, list.size());
    }

    void assertPendingIdpRequestCommunicationCacheIsEmpty() {
        assertEquals(0, getListFromIterator(getIdpRequestCommunicationCache().iterator()).size());
    }

    void assertResponseCommunicationCacheIsEmpty() {
        assertEquals(0, getListFromIterator(getEidasNodeResponseCommunicationCache().iterator()).size());
    }

    void assertHttpMethodsNotAllowed(String path, String... restrictedMethods) {
        for (String method : restrictedMethods) {
            given()
                    .when().
                    request(method, path).
                    then()
                    .assertThat()
                    .statusCode(405);
        }
    }

    void assertResponseCommunicationCacheContainsUserCancelResponse(String expectedErrorMessage, String expectedInResponseTo) throws SAXException, IOException, ParserConfigurationException {
        List<Cache.Entry<String, String>> list = getListFromIterator(getEidasNodeResponseCommunicationCache().iterator());
        assertEquals(1, list.size());
        assertThat(list.get(0).getKey(), matchesPattern(UUID_REGEX));

        Element responseXml = getXmlDocument(list.get(0).getValue());
        assertThat(responseXml, hasXPath("/lightResponse/id", matchesPattern(UUID_REGEX)));
        assertThat(responseXml, hasXPath("/lightResponse/inResponseToId", equalTo(expectedInResponseTo)));
        assertThat(responseXml, hasXPath("/lightResponse/issuer", equalTo(lightTokenResponseIssuerName)));
        assertThat(responseXml, hasXPath("/lightResponse/status/failure", equalTo("true")));
        assertThat(responseXml, hasXPath("/lightResponse/status/statusMessage", equalTo(expectedErrorMessage)));
        assertThat(responseXml, hasXPath("/lightResponse/status/subStatusCode", equalTo("urn:oasis:names:tc:SAML:2.0:status:RequestDenied")));
        assertThat(responseXml, hasXPath("count(/lightResponse/attributes/attribute)", equalTo("0")));
    }
}

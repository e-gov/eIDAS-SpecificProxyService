package ee.ria.eidas.proxy.specific.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ee.ria.eidas.proxy.specific.SpecificProxyTest;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import io.restassured.RestAssured;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.cache.Cache;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.eidas.proxy.specific.util.LightRequestTestHelper.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

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
    private SpecificProxyService specificProxyService;

    @Autowired
    @Getter
    private SpecificProxyServiceCommunication specificProxyServiceCommunication;

    @Autowired
    @Getter
    private SpecificProxyServiceProperties specificProxyServiceProperties;

    @Mock
    @Getter
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    @Getter
    private ArgumentCaptor<LoggingEvent> captorLoggingEvent;

    @BeforeEach
    public void startTest() {
        clearMockOidcServerMappings();
        clearCommunicationCache();
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(mockAppender);
        RestAssured.port = port;
    }

    @AfterEach
    public void stopTest() {
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.detachAppender(mockAppender);
    }

    @Test
    @Order(1)
    void contextLoads() {
        assertNotNull(specificProxyService, "Should not be null!");
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

    void assertErrorIsLogged(String errorMessage) {
        verify(getMockAppender(), atLeast(1)).doAppend(getCaptorLoggingEvent().capture());

        LoggingEvent lastLoggingEvent = getCaptorLoggingEvent().getValue();
        assertThat(lastLoggingEvent.getLevel(), equalTo(Level.ERROR));
        assertThat(lastLoggingEvent.getFormattedMessage(), startsWith(errorMessage));
    }

    void assertWarningIsLogged(String logger, String... warningMessage) {
        verify(getMockAppender(), atLeast(1)).doAppend(getCaptorLoggingEvent().capture());

        List<String> events = getCaptorLoggingEvent().getAllValues().stream()
                .filter(e -> e.getLevel() == Level.WARN && e.getLoggerName().equals(logger))
                .map(e -> e.getFormattedMessage())
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(warningMessage), events);
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

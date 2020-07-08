package ee.ria.eidas.proxy.specific.service;

import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import ee.ria.eidas.proxy.specific.SpecificProxyTest;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ContextConfiguration;

import java.net.SocketTimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest.OPENID_PROVIDER_WELL_KNOWN_PATH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"eidas.proxy.oidc.metadata.update-schedule=-",
        "eidas.proxy.oidc.metadata.max-attempts=3", "eidas.proxy.oidc.metadata.backoff-delay-in-milliseconds=500",
        "eidas.proxy.oidc.connect-timeout-in-milliseconds=500", "eidas.proxy.oidc.metadata.update-enabled=false"})
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = OIDCProviderMetadataServiceRetryTests.TestContextInitializer.class)
class OIDCProviderMetadataServiceRetryTests extends SpecificProxyTest {

    @Value("${eidas.proxy.oidc.metadata.max-attempts}")
    private int maxAttempts;

    @SpyBean
    private OIDCProviderMetadataService oidcProviderMetadataService;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor postProcessor;

    @BeforeEach
    void beforeEach() {
        assertTrue(postProcessor.getScheduledTasks().isEmpty());
        mockOidcServer.resetRequests();
    }

    @Test
    void successfulRetryBeforeMaxAttempts() {
        OIDCProviderMetadata oidcProviderMetadata = oidcProviderMetadataService.getOidcProviderMetadata();
        assertEquals("https://localhost:9877/oidc/jwks", oidcProviderMetadata.getJWKSetURI().toString());
        setupOidcFirstRequestFailsScenario();
        oidcProviderMetadataService.updateMetadata();
        mockOidcServer.verify(2, getRequestedFor(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH)));
        verify(oidcProviderMetadataService, times(2)).updateMetadata();
        OIDCProviderMetadata updatedOidcProviderMetadata = oidcProviderMetadataService.getOidcProviderMetadata();
        String updatedJWKSetURI = "https://localhost:9999/oidc/jwks";
        assertEquals(updatedJWKSetURI, updatedOidcProviderMetadata.getJWKSetURI().toString());

        assertInfoIsLogged(OIDCProviderMetadataService.class,
                "Updating OIDC metadata for issuer: https://localhost:9877",
                "Updating OIDC metadata for issuer: https://localhost:9877",
                "Successfully updated OIDC metadata for issuer: https://localhost:9877",
                "Successfully updated OIDC token validator for issuer: https://localhost:9877");
    }

    @Test
    void maxRetriesWithExceptionWhenConnectionTimeout() {
        mockOidcServer.stubFor(get(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(1000)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/openid-configuration.json")));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> oidcProviderMetadataService.updateMetadata());
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof SocketTimeoutException);
        assertEquals("Read timed out", cause.getMessage());

        verify(oidcProviderMetadataService, times(maxAttempts)).updateMetadata();
        mockOidcServer.verify(maxAttempts, getRequestedFor(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH)));

        assertInfoIsLogged(OIDCProviderMetadataService.class,
                "Updating OIDC metadata for issuer: https://localhost:9877",
                "Updating OIDC metadata for issuer: https://localhost:9877",
                "Updating OIDC metadata for issuer: https://localhost:9877");
        assertErrorIsLogged(OIDCProviderMetadataService.class, "Unable to update OIDC metadata");
    }

    @Test
    void maxRetriesWithExceptionWhenInvalidHttpStatus() {
        mockOidcServer.stubFor(get(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/openid-configuration.json")));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> oidcProviderMetadataService.updateMetadata());
        assertEquals("Failed to fetch OpenID Connect provider metadata from issuer: https://localhost:9877, " +
                "Invalid response status: 500", exception.getMessage());
        verify(oidcProviderMetadataService, times(maxAttempts)).updateMetadata();
        mockOidcServer.verify(maxAttempts, getRequestedFor(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH)));
        assertInfoIsLogged(OIDCProviderMetadataService.class,
                "Updating OIDC metadata for issuer: https://localhost:9877",
                "Updating OIDC metadata for issuer: https://localhost:9877",
                "Updating OIDC metadata for issuer: https://localhost:9877");
        assertErrorIsLogged(OIDCProviderMetadataService.class, "Unable to update OIDC metadata");
    }

    private void setupOidcFirstRequestFailsScenario() {
        mockOidcServer.stubFor(get(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH))
                .inScenario("First request fails")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("First request made"));
        mockOidcServer.stubFor(get(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH))
                .inScenario("First request fails")
                .whenScenarioStateIs("First request made")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/openid-configuration-updated.json"))
                .willSetStateTo("End"));
    }
}

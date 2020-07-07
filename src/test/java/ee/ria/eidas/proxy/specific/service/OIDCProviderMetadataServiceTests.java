package ee.ria.eidas.proxy.specific.service;

import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import ee.ria.eidas.proxy.specific.SpecificProxyTest;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest.OPENID_PROVIDER_WELL_KNOWN_PATH;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"eidas.proxy.oidc.metadata.update-schedule=0/1 * * * * ?",
        "eidas.proxy.oidc.metadata.max-attempts=3", "eidas.proxy.oidc.metadata.backoff-delay=500",
        "eidas.proxy.oidc.connect-timeout-in-milliseconds=500"})
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = OIDCProviderMetadataServiceTests.TestContextInitializer.class)
class OIDCProviderMetadataServiceTests extends SpecificProxyTest {

    @SpyBean
    private OIDCProviderMetadataService oidcProviderMetadataService;

    @Test
    void metadataUpdates() {
        OIDCProviderMetadata oidcProviderMetadata = oidcProviderMetadataService.getOidcProviderMetadata();
        assertEquals("https://localhost:9877/oidc/jwks", oidcProviderMetadata.getJWKSetURI().toString());

        mockOidcServer.stubFor(get(urlEqualTo(OPENID_PROVIDER_WELL_KNOWN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBodyFile("mock_responses/idp/openid-configuration-updated.json")));
        String updatedJWKSetURI = "https://localhost:9999/oidc/jwks";
        await()
                .atMost(Durations.FIVE_SECONDS)
                .untilAsserted(() -> {
                    assertEquals(updatedJWKSetURI,
                            oidcProviderMetadataService.getOidcProviderMetadata().getJWKSetURI().toString());
                });
        assertInfoIsLogged(OIDCProviderMetadataService.class,
                "Updating OIDC metadata for issuer: https://localhost:9877",
                "Successfully updated OIDC metadata for issuer: https://localhost:9877",
                "Successfully updated OIDC token validator for issuer: https://localhost:9877");
    }
}

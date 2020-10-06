package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.SpecificProxyTest;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties.IdTokenClaimMappingProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = DefaultPropertiesTests.TestContextInitializer.class)
@TestPropertySource(value = "classpath:application-test-default.properties", inheritLocations = false, inheritProperties = false)
@ActiveProfiles(value = "test-default", inheritProfiles = false)
public class DefaultPropertiesTests extends SpecificProxyTest {

    @Autowired
    private Environment env;

    @Test
    void defaultAskConsent() {
        assertTrue(specificProxyServiceProperties.isAskConsent());
    }

    @Test
    void defaultLegalPersonAttributesNotAccepted() {
        assertFalse(specificProxyServiceProperties.isLegalPersonAttributesNotAccepted());
    }

    @Test
    void defaultSupportedSPType() {
        assertThat(specificProxyServiceProperties.getSupportedSpTypes()).containsExactly("public");
    }

    @Disabled
    @Test
    void defaultWebappAllowedHttpMethods() {
        // assertThat(specificProxyServiceProperties.getWebapp().getAllowedHttpMethods()).containsExactly(GET, POST);
    }

    @Test
    void defaultWebappSessionIdCookieName() {
        assertEquals("JSESSIONID", specificProxyServiceProperties.getWebapp().getSessionIdCookieName());
    }

    @Test
    void defaultOidcScope() {
        assertThat(specificProxyServiceProperties.getOidc().getScope()).containsExactly("idcard", "mid");
    }

    @Test
    void defaultOidcAcceptedAmrValues() {
        assertThat(specificProxyServiceProperties.getOidc().getAcceptedAmrValues()).containsExactly("idcard", "mID");
    }

    @Test
    void defaultOidcUiLanguage() {
        assertEquals("et", specificProxyServiceProperties.getOidc().getDefaultUiLanguage());
    }

    @Test
    void defaultOidcMaxClockSkewInSeconds() {
        assertEquals(30, specificProxyServiceProperties.getOidc().getMaxClockSkewInSeconds());
    }

    @Test
    void defaultOidcReadTimeoutInMilliseconds() {
        assertEquals(5000, specificProxyServiceProperties.getOidc().getReadTimeoutInMilliseconds());
    }

    @Test
    void defaultOidcConnectTimeoutInMilliseconds() {
        assertEquals(5000, specificProxyServiceProperties.getOidc().getConnectTimeoutInMilliseconds());
    }

    @Test
    void defaultOidcErrorCodeUserCancel() {
        assertEquals("user_cancel", specificProxyServiceProperties.getOidc().getErrorCodeUserCancel());
    }

    @Test
    void defaultIdTokenClaimMappingProperties() {
        IdTokenClaimMappingProperties responseClaimMapping = specificProxyServiceProperties.getOidc().getResponseClaimMapping();
        assertEquals("$.sub", responseClaimMapping.getSubject());
        assertEquals("$.jti", responseClaimMapping.getId());
        assertEquals("$.iss", responseClaimMapping.getIssuer());
        assertEquals("$.acr", responseClaimMapping.getAcr());
    }

    @Test
    void defaultMonitoringDisabled() {
        assertEquals("*", env.getProperty("management.endpoints.jmx.exposure.exclude"));
        assertEquals("*", env.getProperty("management.endpoints.web.exposure.exclude"));
        assertEquals("/", env.getProperty("management.endpoints.web.base-path"));
        assertEquals("false", env.getProperty("management.health.defaults.enabled"));
    }

    @Test
    void defaultGitMode() {
        assertEquals("full", env.getProperty("management.info.git.mode"));
    }

    @Test
    void defaultHeartbeatSettings() {
        assertEquals("3s", env.getProperty("eidas.proxy.health.dependencies.connect-timeout"));
        assertEquals("30d", env.getProperty("eidas.proxy.health.trust-store-expiration-warning"));
    }
}

package ee.ria.eidas.proxy.specific.monitoring.health;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.time.Duration;

import static com.nimbusds.oauth2.sdk.util.URIUtils.removeTrailingSlash;
import static com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest.OPENID_PROVIDER_WELL_KNOWN_PATH;
import static java.lang.Math.toIntExact;
import static java.net.URI.create;

@Component
public class AuthenticationServiceHealthIndicator extends AbstractHealthIndicator {

    @Autowired
    private SpecificProxyServiceProperties specificProxyServiceProperties;

    @Value("${eidas.proxy.health.dependencies.connect-timeout:3}")
    private Duration connectTimeout;

    public AuthenticationServiceHealthIndicator() {
        super("Authentication service health check failed");
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        URI uri = create(removeTrailingSlash(create(specificProxyServiceProperties.getOidc().getIssuerUrl()))
                + OPENID_PROVIDER_WELL_KNOWN_PATH);
        HttpsURLConnection con = (HttpsURLConnection) uri.toURL().openConnection();
        con.setConnectTimeout(toIntExact(connectTimeout.toMillis()));
        con.setSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault());
        try {
            if (con.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                builder.up();
            } else {
                builder.down();
            }
        } finally {
            con.disconnect();
        }
    }
}

package ee.ria.eidas.proxy.specific.monitoring.health;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

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
        URL url = new URL(specificProxyServiceProperties.getOidc().getIssuerUrl());
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout((int) connectTimeout.getSeconds());
        if (con.getResponseCode() < HTTP_INTERNAL_ERROR) {
            builder.up();
        } else {
            builder.down();
        }
    }
}

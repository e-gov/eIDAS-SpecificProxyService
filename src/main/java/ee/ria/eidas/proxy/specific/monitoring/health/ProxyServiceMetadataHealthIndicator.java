package ee.ria.eidas.proxy.specific.monitoring.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.net.URL;
import java.time.Duration;

import static java.lang.Math.toIntExact;

@Component
public class ProxyServiceMetadataHealthIndicator extends AbstractHealthIndicator {

    @Value("${service.metadata.url}")
    private String serviceMetadataUrl;

    @Value("${eidas.proxy.health.dependencies.connect-timeout:3}")
    private Duration connectTimeout;

    public ProxyServiceMetadataHealthIndicator() {
        super("Proxy service metadata health check failed");
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        URL url = new URL(serviceMetadataUrl);
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
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

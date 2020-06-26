package ee.ria.eidas.proxy.specific.monitoring.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;
import java.time.Duration;

@Component
public class ProxyServiceMetadataHealthIndicator extends AbstractHealthIndicator {
    private SSLContext sslContext;

    @Value("${javax.net.ssl.trustStore}")
    private String trustStore;

    @Value("${javax.net.ssl.trustStorePassword}")
    private String trustStorePassword;

    @Value("${javax.net.ssl.trustStoreType}")
    private String trustStoreType;

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
        con.setConnectTimeout((int) connectTimeout.getSeconds());
        con.setSSLSocketFactory(sslContext.getSocketFactory());
        if (con.getResponseCode() == HttpsURLConnection.HTTP_OK) {
            builder.up();
        } else {
            builder.down();
        }
    }

    @PostConstruct
    public void setupSslContext()
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException {
        KeyStore truststore = KeyStore.getInstance(trustStoreType);
        truststore.load(new FileInputStream(trustStore), trustStorePassword.toCharArray());
        TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(truststore);
        TrustManager[] trustManagers = trustFactory.getTrustManagers();
        sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, trustManagers, new SecureRandom());
    }
}

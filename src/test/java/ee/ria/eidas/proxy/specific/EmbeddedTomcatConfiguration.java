package ee.ria.eidas.proxy.specific;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class EmbeddedTomcatConfiguration {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return customizer -> customizer.addConnectorCustomizers(connector -> {
            connector.setAllowTrace(true);
        });
    }
}

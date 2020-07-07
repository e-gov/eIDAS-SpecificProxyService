package ee.ria.eidas.proxy.specific;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;

@SpringBootApplication
@EnableScheduling
@EnableRetry
public class SpecificProxyApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		Assert.notNull(System.getenv("EIDAS_CONFIG_REPOSITORY"), "Required environment variable EIDAS_CONFIG_REPOSITORY is not set");
		Assert.notNull(System.getenv("SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY"), "Required environment variable SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY is not set");
		SpringApplication.run(SpecificProxyApplication.class, args);
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(SpecificProxyApplication.class);
	}
}

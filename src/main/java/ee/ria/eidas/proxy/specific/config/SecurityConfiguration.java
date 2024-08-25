package ee.ria.eidas.proxy.specific.config;

import ee.ria.eidas.proxy.specific.web.filter.AllowedHttpMethodsFilter;
import ee.ria.eidas.proxy.specific.web.filter.RequestCorrelationAttributesTranslationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.header.HeaderWriterFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Autowired
    private SpecificProxyServiceProperties specificProxyServiceProperties;

    @Autowired(required = false)
    private BuildProperties buildProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .addFilterAfter(new RequestCorrelationAttributesTranslationFilter(buildProperties, specificProxyServiceProperties),
                        SecurityContextHolderFilter.class)
                .addFilterAfter(new AllowedHttpMethodsFilter(specificProxyServiceProperties.getWebapp().getAllowedHttpMethods()),
                        HeaderWriterFilter.class)
                .csrf().disable()
                .headers()
                .contentSecurityPolicy(specificProxyServiceProperties.getWebapp().getContentSecurityPolicy())
                .and()
                .frameOptions().deny()
                .httpStrictTransportSecurity()
                .includeSubDomains(true)
                .maxAgeInSeconds(600000);
        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> {
            StrictHttpFirewall firewall = new StrictHttpFirewall();
            firewall.setUnsafeAllowAnyHttpMethod(true);
            web.httpFirewall(firewall);
        };
    }
}

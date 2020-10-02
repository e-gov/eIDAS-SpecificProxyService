package ee.ria.eidas.proxy.specific.config;

import ee.ria.eidas.proxy.specific.web.filter.AllowedHttpMethodsFilter;
import ee.ria.eidas.proxy.specific.web.filter.RequestCorrelationAttributesTranslationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.header.HeaderWriterFilter;

@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Autowired
    private SpecificProxyServiceProperties specificProxyServiceProperties;

    @Autowired(required = false)
    private BuildProperties buildProperties;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .addFilterAfter(new RequestCorrelationAttributesTranslationFilter(buildProperties, specificProxyServiceProperties),
                        SecurityContextPersistenceFilter.class)
                .addFilterAfter(new AllowedHttpMethodsFilter(specificProxyServiceProperties.getWebapp().getAllowedHttpMethods()),
                        HeaderWriterFilter.class)
                .csrf().disable()
                .headers()
                .frameOptions().deny()
                .httpStrictTransportSecurity()
                .includeSubDomains(true)
                .maxAgeInSeconds(600000);
    }

    @Override
    public void configure(WebSecurity webSecurity) {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setUnsafeAllowAnyHttpMethod(true);
        webSecurity.httpFirewall(firewall);
    }
}

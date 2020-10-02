package ee.ria.eidas.proxy.specific.config;

import ee.ria.eidas.proxy.specific.service.OIDCProviderMetadataService;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.IgniteInstanceInitializer;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.attribute.AttributeRegistries;
import eu.eidas.auth.commons.attribute.AttributeRegistry;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.protocol.eidas.spec.LegalPersonSpec;
import eu.eidas.auth.commons.protocol.eidas.spec.NaturalPersonSpec;
import eu.eidas.auth.commons.protocol.eidas.spec.RepresentativeLegalPersonSpec;
import eu.eidas.auth.commons.protocol.eidas.spec.RepresentativeNaturalPersonSpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.JstlView;

import javax.cache.Cache;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import static ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties.CacheProperties.*;

@Slf4j
@Configuration
@ComponentScan("ee.ria.eidas.proxy.specific")
@EnableConfigurationProperties(SpecificProxyServiceProperties.class)
public class SpecificProxyServiceConfiguration implements WebMvcConfigurer {

    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
    }

    @Bean
    public ViewResolver internalResourceViewResolver() {
        InternalResourceViewResolver bean = new InternalResourceViewResolver();
        bean.setViewClass(JstlView.class);
        bean.setPrefix("/WEB-INF/jsp/");
        bean.setSuffix(".jsp");
        return bean;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/resources/**")
                .addResourceLocations("/resources/").setCachePeriod(3600)
                .resourceChain(true).addResolver(new PathResourceResolver());
    }

    @Bean
    public static PropertySourcesPlaceholderConfigurer properties(
            @Value("#{environment.SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY}/specificCommunicationDefinitionProxyservice.xml")
                    String specificCommunicationConfig,
            @Value("#{environment.EIDAS_CONFIG_REPOSITORY}/eidas.xml")
                    String eidasConfig) throws MalformedURLException {

        Assert.isTrue(new File(specificCommunicationConfig).exists(), "Required configuration file not found: " + specificCommunicationConfig);
        Assert.isTrue(new File(eidasConfig).exists(), "Required configuration file not found: " + eidasConfig);

        PropertySourcesPlaceholderConfigurer ppc = new PropertySourcesPlaceholderConfigurer();
        ppc.setLocations(new FileUrlResource(specificCommunicationConfig), new FileUrlResource(eidasConfig));
        ppc.setIgnoreUnresolvablePlaceholders(false);
        return ppc;
    }

    @Bean
    @Qualifier("eidasAttributeRegistry")
    public AttributeRegistry eidasAttributeRegistry() {
        return AttributeRegistries.copyOf(NaturalPersonSpec.REGISTRY, RepresentativeNaturalPersonSpec.REGISTRY,
                LegalPersonSpec.REGISTRY, RepresentativeLegalPersonSpec.REGISTRY);
    }

    @Lazy
    @Bean
    public Ignite igniteClient(SpecificProxyServiceProperties specificProxyServiceProperties, ResourceLoader resourceLoader) throws IOException {
        SpecificProxyServiceProperties.CacheProperties cacheProperties = specificProxyServiceProperties.getCommunicationCache();
        IgniteInstanceInitializer initializer = new IgniteInstanceInitializer(cacheProperties, resourceLoader);
        initializer.initializeInstance();
        return initializer.getInstance();
    }

    @Lazy
    @Bean("nodeSpecificProxyserviceRequestCache")
    public Cache<String, String> nodeSpecificProxyserviceRequestCache(
            Ignite igniteInstance,
            SpecificProxyServiceProperties specificProxyServiceProperties) {

        String cacheName = getCacheName(specificProxyServiceProperties, INCOMING_NODE_REQUESTS_CACHE);
        return igniteInstance.cache(cacheName);
    }

    @Lazy
    @Bean("nodeSpecificProxyserviceResponseCache")
    public Cache<String, String> nodeSpecificProxyserviceResponseCache(
            Ignite igniteInstance,
            SpecificProxyServiceProperties specificProxyServiceProperties) {

        String cacheName = getCacheName(specificProxyServiceProperties, OUTGOING_NODE_RESPONSES_CACHE);
        return igniteInstance.cache(cacheName);
    }

    @Lazy
    @Bean
    public Cache<String, SpecificProxyServiceCommunication.CorrelatedRequestsHolder> specificMSIdpRequestCorrelationMap(
            Ignite igniteInstance, SpecificProxyServiceProperties specificProxyServiceProperties) {

        String cacheName = getCacheName(specificProxyServiceProperties, IDP_PENDING_REQUESTS_CACHE);
        return igniteInstance.cache(cacheName);
    }

    @Lazy
    @Bean
    public Cache<String, ILightResponse> specificMSIdpConsentCorrelationMap(
            Ignite igniteInstance, SpecificProxyServiceProperties specificProxyServiceProperties) {

        String cacheName = getCacheName(specificProxyServiceProperties, IDP_PENDING_CONSENT_MAP);
        return igniteInstance.cache(cacheName);
    }

    @Bean
    public SpecificProxyService specificProxyService(SpecificProxyServiceProperties specificProxyServiceProperties,
                                                     OIDCProviderMetadataService oidcProviderMetadataService, AttributeRegistry eidasAttributesRegistry) {
        return new SpecificProxyService(specificProxyServiceProperties, oidcProviderMetadataService, eidasAttributesRegistry);
    }

    private String getCacheName(SpecificProxyServiceProperties properties, String cacheName) {
        Map<String, String> nameMapping = properties.getCommunicationCache().getCacheNameMapping();
        Assert.isTrue(nameMapping.containsKey(cacheName), "Cache name mapping is required for " + cacheName + "!");
        return nameMapping.get(cacheName);
    }
}
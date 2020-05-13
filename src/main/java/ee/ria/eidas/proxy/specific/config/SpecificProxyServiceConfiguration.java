package ee.ria.eidas.proxy.specific.config;

import com.google.common.collect.ImmutableSortedSet;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.service.SpecificProxyserviceCommunicationServiceImpl;
import ee.ria.eidas.proxy.specific.storage.StoredMSProxyServiceRequestCorrelationMap;
import eu.eidas.auth.cache.ConcurrentCacheServiceIgniteSpecificCommunicationImpl;
import eu.eidas.auth.cache.IgniteInstanceInitializerSpecificCommunication;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.AttributeRegistries;
import eu.eidas.auth.commons.attribute.AttributeRegistry;
import eu.eidas.auth.commons.exceptions.InvalidParameterEIDASException;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.specificcommunication.protocol.SpecificCommunicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.FileUrlResource;
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
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.HashSet;

// TODO ignite instance client mode
// TODO oidc metadata requires loading retry mechanism and update policy
// TODO configuration reloading support should be considered?

@Slf4j
@Configuration
@ComponentScan("ee.ria.eidas.proxy.specific")
@EnableConfigurationProperties(SpecificProxyServiceProperties.class)
public class SpecificProxyServiceConfiguration implements WebMvcConfigurer {

    public static final String CACHE_NAME_RESPONSE = "specificNodeProxyserviceResponseCache";
    public static final String CACHE_NAME_REQUEST = "nodeSpecificProxyserviceRequestCache";

    public static final String CACHE_NAME_IDP_REQUEST_RESPONSE = "specificMSIdpRequestCorrelationMap";
    public static final String CACHE_NAME_IDP_RESPONSE_CONSENT = "specificMSIdpConsentCorrelationMap";


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
            String specificCommunicationDefinitionProperties) throws MalformedURLException {

        Assert.isTrue(new File(specificCommunicationDefinitionProperties).exists(), "Required configuration file not found: " + specificCommunicationDefinitionProperties);

        PropertySourcesPlaceholderConfigurer ppc = new PropertySourcesPlaceholderConfigurer();
        ppc.setLocations(
                new FileUrlResource(specificCommunicationDefinitionProperties)
         );
        ppc.setIgnoreUnresolvablePlaceholders( false );
        return ppc;
    }

    @Bean
    OIDCProviderMetadata oidcProviderMetadata(SpecificProxyServiceProperties specificProxyServiceProperties) {
        try {
            Issuer issuer = new Issuer(specificProxyServiceProperties.getOidc().getIssuerUrl());

            OIDCProviderConfigurationRequest request = new OIDCProviderConfigurationRequest(issuer);
            HTTPRequest httpRequest = request.toHTTPRequest();
            log.info("Fetching OIDC metadata for issuer: " + specificProxyServiceProperties.getOidc().getIssuerUrl());
            HTTPResponse httpResponse = httpRequest.send();

            return OIDCProviderMetadata.parse(httpResponse.getContentAsJSONObject());

        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch OpenID Connect provider metadata from issuer: " + specificProxyServiceProperties.getOidc().getIssuerUrl(), e);
        }
    }

    @Bean
    @Qualifier("attributeRegistry") // TODO remove this and replace with eidasAttributeRegistry
    public Collection<AttributeDefinition<?>> attributeRegistry(
            @Value("#{environment.SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY}/eidas-attributes.xml")
            String eidasAttributesConfiguration) {

        Assert.isTrue(new File(eidasAttributesConfiguration).exists(), "Required configuration file not found: " + eidasAttributesConfiguration);

        Collection<AttributeDefinition<?>> registry = new HashSet<>();
        registry.addAll(AttributeRegistries.fromFiles(eidasAttributesConfiguration, null).getAttributes());
        return ImmutableSortedSet.copyOf(registry);
    }

    @Bean
    @Qualifier("eidasAttributeRegistry")
    public AttributeRegistry eidasAttributeRegistry(@Value("#{environment.SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY}/eidas-attributes.xml")
                                                                String eidasAttributesConfiguration) {

        Assert.isTrue(new File(eidasAttributesConfiguration).exists(), "Required configuration file not found: " + eidasAttributesConfiguration);
        return AttributeRegistries.fromFiles(eidasAttributesConfiguration, null);
    }

    @Bean("eidasIgniteInstanceInitializerSpecificCommunication")
    @Lazy
    public IgniteInstanceInitializerSpecificCommunication igniteInstance(
            @Value("#{environment.EIDAS_CONFIG_REPOSITORY}/igniteSpecificCommunication.xml") String igniteConfigurationFile,
            SpecificProxyServiceProperties specificProxyServiceProperties) throws FileNotFoundException {

        Assert.isTrue(new File(igniteConfigurationFile).exists(), "Required configuration file not found: " + igniteConfigurationFile);

        IgniteInstanceInitializerSpecificCommunication igniteInstance = new IgniteInstanceInitializerSpecificCommunication();

        igniteInstance.setConfigFileName(igniteConfigurationFile);
        igniteInstance.initializeInstance();
        return igniteInstance;
    }

    @Bean("nodeSpecificProxyserviceRequestCache")
    public Cache<String, String> nodeSpecificProxyserviceRequestCache(@Qualifier("eidasIgniteInstanceInitializerSpecificCommunication")
                                                                              IgniteInstanceInitializerSpecificCommunication igniteInstance) {
        return initIgniteCache(igniteInstance, CACHE_NAME_REQUEST);
    }

    @Bean("nodeSpecificProxyserviceResponseCache")
    public Cache<String, String> nodeSpecificProxyserviceResponseCache(@Qualifier("eidasIgniteInstanceInitializerSpecificCommunication")
                                                                              IgniteInstanceInitializerSpecificCommunication igniteInstance) {
        return initIgniteCache(igniteInstance, CACHE_NAME_RESPONSE);
    }

    @Bean
    public Cache<String, StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder> specificMSIdpRequestCorrelationMap(
            @Qualifier("eidasIgniteInstanceInitializerSpecificCommunication")
            IgniteInstanceInitializerSpecificCommunication igniteInstance) {

        return initIgniteCache(igniteInstance, CACHE_NAME_IDP_RESPONSE_CONSENT);
    }

    @Bean
    public Cache<String, ILightResponse> specificMSIdpConsentCorrelationMap(
            @Qualifier("eidasIgniteInstanceInitializerSpecificCommunication")
                    IgniteInstanceInitializerSpecificCommunication igniteInstance) {

        return initIgniteCache(igniteInstance, CACHE_NAME_IDP_REQUEST_RESPONSE);
    }


    @Bean("springManagedSpecificProxyserviceCommunicationService")
    public SpecificCommunicationService specificCommunicationService(SpecificProxyServiceProperties specificProxyServiceProperties,
        @Qualifier("nodeSpecificProxyserviceRequestCache")
        Cache requestCommunicationCache,
        @Qualifier("nodeSpecificProxyserviceResponseCache")
        Cache responseCommunicationCache) {
        return new SpecificProxyserviceCommunicationServiceImpl(requestCommunicationCache, responseCommunicationCache);
    }

    @Bean
    public SpecificProxyService specificProxyService(
            OIDCProviderMetadata oidcProviderMetadata,
            SpecificProxyServiceProperties specificProxyServiceProperties,
            Cache<String, StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder> specificMSIdpRequestCorrelationMap,
            Cache<String, ILightResponse> specificMSIdpConsentCorrelationMap) {

        return new SpecificProxyService(
                oidcProviderMetadata, specificProxyServiceProperties,
                specificMSIdpRequestCorrelationMap,
                specificMSIdpConsentCorrelationMap);
    }

    private Cache initIgniteCache(@Qualifier("eidasIgniteInstanceInitializerSpecificCommunication") IgniteInstanceInitializerSpecificCommunication igniteInstance, String cacheName) {
        ConcurrentCacheServiceIgniteSpecificCommunicationImpl igniteCache = new ConcurrentCacheServiceIgniteSpecificCommunicationImpl();

        igniteCache.setCacheName(cacheName);
        igniteCache.setIgniteInstanceInitializerSpecificCommunication(igniteInstance);

        try {
            return new StoredMSProxyServiceRequestCorrelationMap(igniteCache);
        } catch (InvalidParameterEIDASException e) {
            throw new IllegalStateException("Problem with your Ignite configuration! Failed to instantiate Ignite cache named '" + cacheName + "'. Please check your configuration!", e);
        }
    }
}
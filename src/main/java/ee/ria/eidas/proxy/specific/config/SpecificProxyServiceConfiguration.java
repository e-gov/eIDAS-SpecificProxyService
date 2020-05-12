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
import ee.ria.eidas.proxy.specific.storage.StoredNodeRequestCorrelationMap;
import eu.eidas.auth.cache.ConcurrentCacheServiceIgniteSpecificCommunicationImpl;
import eu.eidas.auth.cache.IgniteInstanceInitializerSpecificCommunication;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.AttributeRegistries;
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
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

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

    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
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
    @Qualifier("attributeRegistry")
    public Collection<AttributeDefinition<?>> attributeRegistry(
            @Value("#{environment.SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY}/eidas-attributes.xml")
            String eidasAttributesConfiguration) {

        Assert.isTrue(new File(eidasAttributesConfiguration).exists(), "Required configuration file not found: " + eidasAttributesConfiguration);

        Collection<AttributeDefinition<?>> registry = new HashSet<>();
        registry.addAll(AttributeRegistries.fromFiles(eidasAttributesConfiguration, null).getAttributes());
        return ImmutableSortedSet.copyOf(registry);
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
        ConcurrentCacheServiceIgniteSpecificCommunicationImpl igniteCache = new ConcurrentCacheServiceIgniteSpecificCommunicationImpl();

        igniteCache.setCacheName("nodeSpecificProxyserviceRequestCache");
        igniteCache.setIgniteInstanceInitializerSpecificCommunication(igniteInstance);

        return new StoredNodeRequestCorrelationMap(igniteCache);
    }

    @Bean
    public Cache<String, StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder> specificMSIdpRequestCorrelationMap(
            @Qualifier("eidasIgniteInstanceInitializerSpecificCommunication")
            IgniteInstanceInitializerSpecificCommunication igniteInstance) {

        ConcurrentCacheServiceIgniteSpecificCommunicationImpl igniteCache = new ConcurrentCacheServiceIgniteSpecificCommunicationImpl();

        igniteCache.setCacheName("specificMSIdpRequestCorrelationMap");
        igniteCache.setIgniteInstanceInitializerSpecificCommunication(igniteInstance);

        return new StoredMSProxyServiceRequestCorrelationMap(igniteCache);
    }


    @Bean("springManagedSpecificProxyserviceCommunicationService")
    public SpecificCommunicationService specificCommunicationService(SpecificProxyServiceProperties specificProxyServiceProperties,
        @Qualifier("nodeSpecificProxyserviceRequestCache")
        Cache specificNodeProxyserviceRequestCommunicationCache) {
        return new SpecificProxyserviceCommunicationServiceImpl(specificNodeProxyserviceRequestCommunicationCache);
    }

    @Bean
    public SpecificProxyService specificProxyService(
            OIDCProviderMetadata oidcProviderMetadata,
            SpecificProxyServiceProperties specificProxyServiceProperties,
            Cache specificMSIdpRequestCorrelationMap) {

        return new SpecificProxyService(
                oidcProviderMetadata, specificProxyServiceProperties,
                specificMSIdpRequestCorrelationMap);
    }
}
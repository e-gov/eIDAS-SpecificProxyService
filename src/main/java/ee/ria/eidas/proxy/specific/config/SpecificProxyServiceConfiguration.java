package ee.ria.eidas.proxy.specific.config;

import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import ee.ria.eidas.proxy.specific.web.filter.DisabledHttpMethodsFilter;
import eu.eidas.auth.cache.ConcurrentCacheServiceIgniteSpecificCommunicationImpl;
import eu.eidas.auth.cache.IgniteInstanceInitializerSpecificCommunication;
import eu.eidas.auth.commons.attribute.AttributeRegistries;
import eu.eidas.auth.commons.attribute.AttributeRegistry;
import eu.eidas.auth.commons.exceptions.InvalidParameterEIDASException;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.protocol.eidas.spec.LegalPersonSpec;
import eu.eidas.auth.commons.protocol.eidas.spec.NaturalPersonSpec;
import eu.eidas.auth.commons.protocol.eidas.spec.RepresentativeLegalPersonSpec;
import eu.eidas.auth.commons.protocol.eidas.spec.RepresentativeNaturalPersonSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import java.util.HashMap;

import static com.nimbusds.jose.JWSAlgorithm.RS256;

// TODO ignite instance client mode

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

    @Bean
    public FilterRegistrationBean disableExtraHttpMethodsFilter(SpecificProxyServiceProperties specificProxyServiceProperties) {
        final FilterRegistrationBean bean = new FilterRegistrationBean();
        bean.setFilter(new DisabledHttpMethodsFilter(specificProxyServiceProperties.getWebapp().getDisabledHttpMethods()));
        bean.setInitParameters(new HashMap<>());
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

        assertFileExists(specificCommunicationDefinitionProperties);

        PropertySourcesPlaceholderConfigurer ppc = new PropertySourcesPlaceholderConfigurer();
        ppc.setLocations(
                new FileUrlResource(specificCommunicationDefinitionProperties)
         );
        ppc.setIgnoreUnresolvablePlaceholders( false );
        return ppc;
    }

    @Bean
    public OIDCProviderMetadata oidcProviderMetadata(SpecificProxyServiceProperties specificProxyServiceProperties) {
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
    public IDTokenValidator getIdTokenValidator(OIDCProviderMetadata oidcProviderMetadata, SpecificProxyServiceProperties specificProxyServiceProperties) throws MalformedURLException {
        Issuer iss = new Issuer(oidcProviderMetadata.getIssuer());
        ClientID clientID = new ClientID(specificProxyServiceProperties.getOidc().getClientId());
        IDTokenValidator validator = new IDTokenValidator(iss, clientID, RS256, oidcProviderMetadata.getJWKSetURI().toURL());
        validator.setMaxClockSkew(specificProxyServiceProperties.getOidc().getMaxClockSkewInSeconds());
        return validator;
    }

    @Bean
    @Qualifier("eidasAttributeRegistry")
    public AttributeRegistry eidasAttributeRegistry() {
        return AttributeRegistries.copyOf(NaturalPersonSpec.REGISTRY, RepresentativeNaturalPersonSpec.REGISTRY,
                LegalPersonSpec.REGISTRY, RepresentativeLegalPersonSpec.REGISTRY);
    }

    @Bean("eidasIgniteInstanceInitializerSpecificCommunication")
    @Lazy
    public IgniteInstanceInitializerSpecificCommunication igniteInstance(
            @Value("#{environment.EIDAS_CONFIG_REPOSITORY}/igniteSpecificCommunication.xml") String igniteConfigurationFile,
            SpecificProxyServiceProperties specificProxyServiceProperties) throws FileNotFoundException {

        assertFileExists(igniteConfigurationFile);

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
    public Cache<String, SpecificProxyServiceCommunication.CorrelatedRequestsHolder> specificMSIdpRequestCorrelationMap(
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

    @Bean
    public SpecificProxyService specificProxyService(SpecificProxyServiceProperties specificProxyServiceProperties,
                                                     OIDCProviderMetadata oidcProviderMetadata,
                                                     IDTokenValidator idTokenValidator,
                                                     AttributeRegistry eidasAttributesRegistry) {

        return new SpecificProxyService(specificProxyServiceProperties, oidcProviderMetadata, idTokenValidator, eidasAttributesRegistry);
    }

    private Cache initIgniteCache(@Qualifier("eidasIgniteInstanceInitializerSpecificCommunication") IgniteInstanceInitializerSpecificCommunication igniteInstance, String cacheName) {

        try {
            ConcurrentCacheServiceIgniteSpecificCommunicationImpl igniteCache = new ConcurrentCacheServiceIgniteSpecificCommunicationImpl();
            igniteCache.setCacheName(cacheName);
            igniteCache.setIgniteInstanceInitializerSpecificCommunication(igniteInstance);
            return igniteCache.getConfiguredCache();
        } catch (InvalidParameterEIDASException e) {
            throw new IllegalStateException("Problem with your Ignite configuration! Failed to instantiate Ignite cache named '" + cacheName + "'. Please check your configuration!", e);
        }

    }

    private static void assertFileExists(String configFile) {
        Assert.isTrue(new File(configFile).exists(), "Required configuration file not found: " + configFile);
    }
}
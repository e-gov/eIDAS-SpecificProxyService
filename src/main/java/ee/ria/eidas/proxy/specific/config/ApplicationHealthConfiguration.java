package ee.ria.eidas.proxy.specific.config;


import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.registry.DefaultHealthContributorRegistry;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationHealthConfiguration {

    @Bean
    @ConditionalOnExpression("!'${management.endpoints.web.exposure.include}'.contains('health')" +
            "or '${management.endpoints.web.exposure.exclude}'.contains('health')")
    public HealthContributorRegistry healthContributorRegistry(ApplicationContext ctx) {
        DefaultHealthContributorRegistry registry = new DefaultHealthContributorRegistry();

        ctx.getBeansOfType(HealthContributor.class)
                .forEach(registry::registerContributor);

        return registry;
    }
}

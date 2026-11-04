package com.autumn.cloud.extension.discovery.consul;

import com.autumn.cloud.extension.core.consul.ConsulClient;
import com.autumn.cloud.extension.core.consul.ConsulSettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnDiscoveryEnabled
@ConditionalOnProperty(prefix = "autumn.discovery", name = "backend", havingValue = "consul")
@EnableConfigurationProperties({ConsulProperties.class, AutumnDiscoveryProperties.class})
public class ConsulAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ConsulClient autumnConsulClient(ConsulProperties properties) {
        return new ConsulClient(toSettings(properties));
    }

    @Bean
    @ConditionalOnMissingBean(ConsulDiscoveryClient.class)
    public ConsulDiscoveryClient autumnConsulDiscoveryClient(ConsulClient client) {
        return new ConsulDiscoveryClient(client);
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveDiscoveryClientAdapter.class)
    public ReactiveDiscoveryClientAdapter autumnConsulReactiveDiscoveryClient(ConsulDiscoveryClient client) {
        return new ReactiveDiscoveryClientAdapter(client);
    }

    @Bean
    @ConditionalOnProperty(prefix = "autumn.discovery", name = "register", havingValue = "true", matchIfMissing = true)
    public ConsulServiceRegistry autumnConsulServiceRegistry(ConsulClient client, ConsulProperties properties) {
        return new ConsulServiceRegistry(client, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "autumn.discovery", name = "register", havingValue = "true", matchIfMissing = true)
    public AutumnRegistration autumnConsulRegistration(AutumnDiscoveryProperties properties,
                                                       Environment environment) {
        return new AutumnRegistration(Registrations.build(properties, environment));
    }

    @Bean
    @ConditionalOnProperty(prefix = "autumn.discovery", name = "register", havingValue = "true", matchIfMissing = true)
    public AutumnAutoServiceRegistration autumnConsulAutoServiceRegistration(
            ApplicationContext context, ConsulServiceRegistry registry, AutumnRegistration registration) {
        return new AutumnAutoServiceRegistration(context, registry, registration);
    }

    private static ConsulSettings toSettings(ConsulProperties p) {
        return new ConsulSettings(p.host(), p.port(), p.scheme(), p.token(), p.healthCheckTtlSeconds());
    }
}

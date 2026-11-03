package com.autumn.cloud.extension.nacos;

import com.autumn.cloud.extension.config.AutumnAutoServiceRegistration;
import com.autumn.cloud.extension.config.AutumnDiscoveryProperties;
import com.autumn.cloud.extension.config.AutumnRegistration;
import com.autumn.cloud.extension.config.ReactiveDiscoveryClientAdapter;
import com.autumn.cloud.extension.config.Registrations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Nacos 后端自动装配：{@code autumn.discovery.backend=nacos} 时加载。
 */
@AutoConfiguration
@ConditionalOnDiscoveryEnabled
@ConditionalOnProperty(prefix = "autumn.discovery", name = "backend", havingValue = "nacos")
@EnableConfigurationProperties({NacosProperties.class, AutumnDiscoveryProperties.class})
public class NacosAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public NacosClient autumnNacosClient(NacosProperties properties) {
        return new NacosClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean(NacosDiscoveryClient.class)
    public NacosDiscoveryClient autumnNacosDiscoveryClient(NacosClient client) {
        return new NacosDiscoveryClient(client);
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveDiscoveryClientAdapter.class)
    public ReactiveDiscoveryClientAdapter autumnNacosReactiveDiscoveryClient(NacosDiscoveryClient client) {
        return new ReactiveDiscoveryClientAdapter(client);
    }

    @Bean
    @ConditionalOnProperty(prefix = "autumn.discovery", name = "register", havingValue = "true", matchIfMissing = true)
    public NacosServiceRegistry autumnNacosServiceRegistry(NacosClient client, NacosProperties properties) {
        return new NacosServiceRegistry(client, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "autumn.discovery", name = "register", havingValue = "true", matchIfMissing = true)
    public AutumnRegistration autumnNacosRegistration(AutumnDiscoveryProperties properties,
                                                      Environment environment) {
        return new AutumnRegistration(Registrations.build(properties, environment));
    }

    @Bean
    @ConditionalOnProperty(prefix = "autumn.discovery", name = "register", havingValue = "true", matchIfMissing = true)
    public AutumnAutoServiceRegistration autumnNacosAutoServiceRegistration(
            ApplicationContext context, NacosServiceRegistry registry, AutumnRegistration registration) {
        return new AutumnAutoServiceRegistration(context, registry, registration);
    }
}

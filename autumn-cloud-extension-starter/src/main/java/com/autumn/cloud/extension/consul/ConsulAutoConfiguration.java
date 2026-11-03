package com.autumn.cloud.extension.consul;

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
 * Consul 后端自动装配：{@code autumn.discovery.backend=consul} 时加载。
 *
 * <p>三种后端（consul/nacos/multicast）通过该属性三选一互斥装配，
 * 避免 multicast 示例与真实注册中心同时生效的冲突。</p>
 */
@AutoConfiguration
@ConditionalOnDiscoveryEnabled
@ConditionalOnProperty(prefix = "autumn.discovery", name = "backend", havingValue = "consul")
@EnableConfigurationProperties({ConsulProperties.class, AutumnDiscoveryProperties.class})
public class ConsulAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ConsulClient autumnConsulClient(ConsulProperties properties) {
        return new ConsulClient(properties);
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
}

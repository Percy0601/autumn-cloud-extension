package com.autumn.cloud.extension.multicast;

import com.autumn.cloud.extension.config.AutumnAutoServiceRegistration;
import com.autumn.cloud.extension.config.AutumnDiscoveryProperties;
import com.autumn.cloud.extension.config.AutumnRegistration;
import com.autumn.cloud.extension.config.ReactiveDiscoveryClientAdapter;
import com.autumn.cloud.extension.config.Registrations;
import com.autumn.cloud.extension.core.multicast.MulticastConfig;
import com.autumn.cloud.extension.core.multicast.MulticastDiscovery;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 组播后端自动装配：{@code autumn.discovery.backend=multicast} 时加载。
 *
 * <p>组播后端零外部依赖，纯 JDK 实现，适合开发环境/小型局域网。</p>
 *
 * <p>条件装配：</p>
 * <ul>
 *     <li>{@code @ConditionalOnDiscoveryEnabled} - Spring Cloud Discovery 启用时</li>
 *     <li>{@code @ConditionalOnProperty(backend=multicast)} - 显式指定 multicast 后端</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnDiscoveryEnabled
@ConditionalOnProperty(prefix = "autumn.discovery", name = "backend", havingValue = "multicast")
@EnableConfigurationProperties({MulticastDiscoveryProperties.class, AutumnDiscoveryProperties.class})
public class MulticastAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public MulticastDiscovery autumnMulticastDiscovery(MulticastDiscoveryProperties properties) {
        return new MulticastDiscovery(new MulticastConfig(properties.group(), properties.port(), properties.ttl(),
                properties.heartbeatIntervalMs(), properties.evictAfterMs(), properties.networkInterface()));
    }

    @Bean
    @ConditionalOnMissingBean(MulticastDiscoveryClient.class)
    public MulticastDiscoveryClient autumnMulticastDiscoveryClient(MulticastDiscovery discovery) {
        return new MulticastDiscoveryClient(discovery);
    }

    /** 网关等纯消费方（register=false）也需要加入组播组接收实例广播 */
    @Bean
    @ConditionalOnMissingBean(MulticastDiscoveryLifecycle.class)
    public MulticastDiscoveryLifecycle autumnMulticastDiscoveryLifecycle(MulticastDiscovery discovery) {
        return new MulticastDiscoveryLifecycle(discovery);
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveDiscoveryClientAdapter.class)
    public ReactiveDiscoveryClientAdapter autumnMulticastReactiveDiscoveryClient(MulticastDiscoveryClient client) {
        return new ReactiveDiscoveryClientAdapter(client);
    }

    @Bean
    @ConditionalOnProperty(prefix = "autumn.discovery", name = "register", havingValue = "true", matchIfMissing = true)
    public MulticastServiceRegistry autumnMulticastServiceRegistry(MulticastDiscovery discovery) {
        return new MulticastServiceRegistry(discovery);
    }

    @Bean
    @ConditionalOnProperty(prefix = "autumn.discovery", name = "register", havingValue = "true", matchIfMissing = true)
    public AutumnRegistration autumnMulticastRegistration(AutumnDiscoveryProperties properties,
                                                          Environment environment) {
        return new AutumnRegistration(Registrations.build(properties, environment));
    }

    @Bean
    @ConditionalOnProperty(prefix = "autumn.discovery", name = "register", havingValue = "true", matchIfMissing = true)
    public AutumnAutoServiceRegistration autumnMulticastAutoServiceRegistration(
            ApplicationContext context, MulticastServiceRegistry registry, AutumnRegistration registration) {
        return new AutumnAutoServiceRegistration(context, registry, registration);
    }
}

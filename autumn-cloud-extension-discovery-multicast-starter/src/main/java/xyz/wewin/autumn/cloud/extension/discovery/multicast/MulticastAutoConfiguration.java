package xyz.wewin.autumn.cloud.extension.discovery.multicast;

import xyz.wewin.autumn.cloud.extension.core.multicast.MulticastConfig;
import xyz.wewin.autumn.cloud.extension.core.multicast.MulticastDiscovery;
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
@ConditionalOnProperty(prefix = "autumn.discovery", name = "backend", havingValue = "multicast", matchIfMissing = true)
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

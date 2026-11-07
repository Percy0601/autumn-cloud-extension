package xyz.wewin.autumn.cloud.extension.traffic;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

public class TrafficLoadBalancerConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceInstanceListSupplier trafficServiceInstanceListSupplier(
            DiscoveryClient discoveryClient, Environment environment, TrafficProperties properties) {
        return new TrafficServiceInstanceListSupplier(discoveryClient, environment, properties);
    }
}

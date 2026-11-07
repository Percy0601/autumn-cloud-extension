package xyz.wewin.autumn.cloud.extension.traffic;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;

@AutoConfiguration
@ConditionalOnDiscoveryEnabled
@ConditionalOnClass(ServiceInstanceListSupplier.class)
@EnableConfigurationProperties(TrafficProperties.class)
@LoadBalancerClients(defaultConfiguration = TrafficLoadBalancerConfiguration.class)
public class AutumnTrafficAutoConfiguration {
}

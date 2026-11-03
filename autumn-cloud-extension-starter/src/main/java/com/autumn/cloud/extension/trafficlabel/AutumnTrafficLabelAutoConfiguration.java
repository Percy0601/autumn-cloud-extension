package com.autumn.cloud.extension.trafficlabel;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;

/**
 * 流量标签路由自动装配：classpath 存在 spring-cloud-loadbalancer 时启用。
 */
@AutoConfiguration
@ConditionalOnDiscoveryEnabled
@ConditionalOnClass(ServiceInstanceListSupplier.class)
@EnableConfigurationProperties(TrafficLabelProperties.class)
@LoadBalancerClients(defaultConfiguration = TrafficLabelLoadBalancerConfiguration.class)
public class AutumnTrafficLabelAutoConfiguration {
}

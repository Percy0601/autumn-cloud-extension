package com.autumn.cloud.extension.traffic;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * LoadBalancer 子上下文配置：为每个 lb:// 服务挂上流量过滤。
 *
 * <p>注意：本类不能加 @Configuration，也不能被组件扫描到——
 * 它只能作为 {@code @LoadBalancerClients(defaultConfiguration = ...)} 加载进 lb:// 子上下文
 * （子上下文的 Environment 才携带 loadbalancer.client.name）。</p>
 */
public class TrafficLoadBalancerConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceInstanceListSupplier trafficServiceInstanceListSupplier(
            DiscoveryClient discoveryClient, Environment environment, TrafficProperties properties) {
        return new TrafficServiceInstanceListSupplier(discoveryClient, environment, properties);
    }
}

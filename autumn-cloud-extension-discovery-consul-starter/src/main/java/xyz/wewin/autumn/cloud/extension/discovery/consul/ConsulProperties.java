package xyz.wewin.autumn.cloud.extension.discovery.consul;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Consul 服务发现 Spring Boot 配置绑定。
 */
@ConfigurationProperties(prefix = "autumn.discovery.consul")
public record ConsulProperties(
        @DefaultValue("127.0.0.1") String host,
        @DefaultValue("8500") int port,
        @DefaultValue("http") String scheme,
        String token,
        String namespace,
        @DefaultValue("30") int healthCheckTtlSeconds) {
}

package com.autumn.cloud.extension.consul;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Consul HTTP API 配置。
 */
@ConfigurationProperties(prefix = "autumn.discovery.consul")
public record ConsulProperties(
        @DefaultValue("127.0.0.1") String host,
        @DefaultValue("8500") int port,
        @DefaultValue("http") String scheme,
        /** ACL Token，未启用 ACL 时无需配置 */
        String token,
        /** TTL 健康检查周期（秒），自动按 TTL/3 续约 */
        @DefaultValue("30") int healthCheckTtlSeconds) {
}

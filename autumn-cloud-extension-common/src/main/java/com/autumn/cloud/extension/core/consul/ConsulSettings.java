package com.autumn.cloud.extension.core.consul;

/**
 * Consul Agent 连接配置（纯 Java，无 Spring 依赖）。
 *
 * @param host                   Consul Agent 地址
 * @param port                   Consul Agent 端口
 * @param scheme                 http / https
 * @param token                  ACL Token（未启用 ACL 时为 null）
 * @param namespace              Enterprise Namespace（社区版 / 默认分区传 null）
 * @param healthCheckTtlSeconds  TTL 健康检查周期（秒），自动按 TTL/3 续约
 */
public record ConsulSettings(
        String host, int port, String scheme, String token, String namespace, int healthCheckTtlSeconds) {

    public ConsulSettings {
        host = (host == null || host.isBlank()) ? "127.0.0.1" : host.trim();
        port = port <= 0 ? 8500 : port;
        scheme = (scheme == null || scheme.isBlank()) ? "http" : scheme.trim();
        namespace = (namespace == null || namespace.isBlank()) ? null : namespace.trim();
        healthCheckTtlSeconds = healthCheckTtlSeconds <= 0 ? 30 : healthCheckTtlSeconds;
    }

    public static ConsulSettings defaults() {
        return new ConsulSettings("127.0.0.1", 8500, "http", null, null, 30);
    }
}

package com.autumn.cloud.extension.core.consul;

/**
 * Consul KV 配置中心连接配置（纯 Java，无 Spring 依赖）。
 *
 * @param host         Consul Agent 地址
 * @param port         Consul Agent 端口
 * @param scheme       http / https
 * @param token        ACL Token
 * @param datacenter   数据中心
 * @param defaultGroup 默认分组（对应 Consul KV 路径前缀）
 */
public record ConsulConfigSettings(
        String host, int port, String scheme, String token, String datacenter, String defaultGroup) {

    public ConsulConfigSettings {
        host = (host == null || host.isBlank()) ? "127.0.0.1" : host.trim();
        port = port <= 0 ? 8500 : port;
        scheme = (scheme == null || scheme.isBlank()) ? "http" : scheme.trim();
        datacenter = (datacenter == null || datacenter.isBlank()) ? "dc1" : datacenter.trim();
        defaultGroup = (defaultGroup == null || defaultGroup.isBlank()) ? "DEFAULT_GROUP" : defaultGroup.trim();
    }

    public static ConsulConfigSettings defaults() {
        return new ConsulConfigSettings("127.0.0.1", 8500, "http", null, "dc1", "DEFAULT_GROUP");
    }
}

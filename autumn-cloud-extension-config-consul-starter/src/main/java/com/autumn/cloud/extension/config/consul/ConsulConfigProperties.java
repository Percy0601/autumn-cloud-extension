package com.autumn.cloud.extension.config.consul;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Consul 配置中心配置。
 */
@ConfigurationProperties(prefix = "autumn.config.consul")
public record ConsulConfigProperties(
        @DefaultValue("127.0.0.1") String host,
        @DefaultValue("8500") int port,
        @DefaultValue("http") String scheme,
        /** ACL Token */
        String token,
        /** 数据中心 */
        @DefaultValue("dc1") String datacenter,
        /** 默认分组（对应 Consul KV 路径前缀） */
        @DefaultValue("DEFAULT_GROUP") String defaultGroup) {
}

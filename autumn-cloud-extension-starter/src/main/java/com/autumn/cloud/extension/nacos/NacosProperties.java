package com.autumn.cloud.extension.nacos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Nacos OpenAPI 配置。
 */
@ConfigurationProperties(prefix = "autumn.discovery.nacos")
public record NacosProperties(
        /** Nacos 服务端地址 host:port */
        @DefaultValue("127.0.0.1:8848") String serverAddr,
        @DefaultValue("public") String namespace,
        @DefaultValue("DEFAULT_GROUP") String group,
        /** 鉴权 Token（未开启鉴权无需配置） */
        String accessToken,
        /** 临时实例心跳间隔（毫秒） */
        @DefaultValue("5000") long beatIntervalMs) {
}

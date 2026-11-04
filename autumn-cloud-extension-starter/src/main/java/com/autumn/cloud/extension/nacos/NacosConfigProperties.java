package com.autumn.cloud.extension.nacos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Nacos 配置中心配置。
 */
@ConfigurationProperties(prefix = "autumn.config.nacos")
public record NacosConfigProperties(
        /** Nacos 服务端地址 host:port */
        @DefaultValue("127.0.0.1:8848") String serverAddr,
        @DefaultValue("public") String namespace,
        @DefaultValue("DEFAULT_GROUP") String defaultGroup,
        /** 鉴权 Token */
        String accessToken,
        /** 长轮询超时（毫秒） */
        @DefaultValue("30000") long pollingTimeoutMs) {
}

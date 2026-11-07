package xyz.wewin.autumn.cloud.extension.config.nacos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Nacos 配置中心 Spring Boot 绑定。
 */
@ConfigurationProperties(prefix = "autumn.config.nacos")
public record NacosConfigProperties(
        @DefaultValue("127.0.0.1:8848") String serverAddr,
        @DefaultValue("public") String namespace,
        @DefaultValue("DEFAULT_GROUP") String defaultGroup,
        String accessToken,
        @DefaultValue("30000") long pollingTimeoutMs) {
}

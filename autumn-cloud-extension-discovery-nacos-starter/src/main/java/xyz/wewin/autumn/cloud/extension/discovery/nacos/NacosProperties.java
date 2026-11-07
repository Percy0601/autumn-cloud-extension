package xyz.wewin.autumn.cloud.extension.discovery.nacos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Nacos 服务发现 Spring Boot 配置绑定。
 */
@ConfigurationProperties(prefix = "autumn.discovery.nacos")
public record NacosProperties(
        @DefaultValue("127.0.0.1:8848") String serverAddr,
        @DefaultValue("public") String namespace,
        @DefaultValue("DEFAULT") String group,
        String accessToken,
        @DefaultValue("5000") long beatIntervalMs) {
}

package xyz.wewin.autumn.cloud.extension.discovery.multicast;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 组播发现 Spring Boot 配置绑定。
 */
@ConfigurationProperties(prefix = "autumn.discovery.multicast")
public record MulticastDiscoveryProperties(
        @DefaultValue("239.192.71.88") String group,
        @DefaultValue("47891") int port,
        @DefaultValue("1") int ttl,
        @DefaultValue("3000") long heartbeatIntervalMs,
        @DefaultValue("15000") long evictAfterMs,
        String networkInterface) {
}

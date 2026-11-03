package com.autumn.cloud.extension.multicast;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 组播发现配置。
 */
@ConfigurationProperties(prefix = "autumn.discovery.multicast")
public record MulticastDiscoveryProperties(
        /** 组播地址（IPv4 组播段 224.0.0.0 ~ 239.255.255.255） */
        @DefaultValue("239.255.42.99") String group,
        @DefaultValue("5555") int port,
        /** TTL，1 = 仅本网段 */
        @DefaultValue("1") int ttl,
        /** 心跳广播间隔（毫秒） */
        @DefaultValue("3000") long heartbeatIntervalMs,
        /** 超时判定（毫秒），建议 ≥ 3 × 心跳间隔 */
        @DefaultValue("15000") long evictAfterMs,
        /** 指定网卡名，缺省自动探测 */
        String networkInterface) {
}

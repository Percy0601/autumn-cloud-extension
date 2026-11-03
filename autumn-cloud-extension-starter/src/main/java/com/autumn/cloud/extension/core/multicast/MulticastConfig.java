package com.autumn.cloud.extension.core.multicast;

/**
 * 组播发现配置。
 *
 * @param group               组播地址（IPv4 组播段 224.0.0.0 ~ 239.255.255.255）
 * @param port                组播端口
 * @param ttl                 TTL，1 = 仅本网段
 * @param heartbeatIntervalMs 心跳广播间隔
 * @param evictAfterMs        超过该时长未收到心跳视为实例下线（建议 ≥ 3 × 心跳间隔）
 * @param networkInterface    指定网卡名；null 自动探测
 */
public record MulticastConfig(String group, int port, int ttl,
                              long heartbeatIntervalMs, long evictAfterMs,
                              String networkInterface) {

    public MulticastConfig {
        group = (group == null || group.isBlank()) ? "239.255.42.99" : group.trim();
        port = port <= 0 ? 5555 : port;
        ttl = ttl <= 0 ? 1 : ttl;
        heartbeatIntervalMs = heartbeatIntervalMs <= 0 ? 3000 : heartbeatIntervalMs;
        evictAfterMs = evictAfterMs <= 0 ? Math.max(heartbeatIntervalMs * 3, 10_000) : evictAfterMs;
    }
}

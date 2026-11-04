package com.autumn.cloud.extension.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

/**
 * 注册侧通用配置（三种后端共用）。
 *
 * <p>后端选择通过 {@code autumn.discovery.backend=consul|nacos|multicast} 三选一互斥加载。</p>
 */
@ConfigurationProperties(prefix = "autumn.discovery")
public record AutumnDiscoveryProperties(
        /** 是否注册自身到注册中心 */
        @DefaultValue("true") boolean register,
        /** 服务名，缺省取 spring.application.name */
        String service,
        /** 注册地址，缺省自动探测本机 IP */
        String host,
        /** 注册端口，缺省取 server.port（再缺省 8080） */
        Integer port,
        /** 本实例所属流量，写入 metadata["traffic"]；缺省视为 main 主干流量 */
        String traffic,
        /** 附加元数据 */
        Map<String, String> metadata) {

    public AutumnDiscoveryProperties {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}

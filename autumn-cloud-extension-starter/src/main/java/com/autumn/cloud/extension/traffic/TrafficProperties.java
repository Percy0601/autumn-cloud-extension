package com.autumn.cloud.extension.traffic;

import com.autumn.cloud.extension.core.traffic.Traffic;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 流量路由配置（网关侧消费）。
 */
@ConfigurationProperties(prefix = "autumn.traffic")
public record TrafficProperties(
        /** 携带流量标识的请求 header */
        @DefaultValue(Traffic.DEFAULT_HEADER) String header,
        /** 目标流量无实例时是否回落到 main 主干 */
        @DefaultValue("true") boolean fallbackToMain) {
}

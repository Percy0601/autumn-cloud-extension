package com.autumn.cloud.extension.trafficlabel;

import com.autumn.cloud.extension.core.trafficlabel.TrafficLabel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 流量标签路由配置（网关侧消费）。
 */
@ConfigurationProperties(prefix = "autumn.traffic-label")
public record TrafficLabelProperties(
        /** 携带流量标签标识的请求 header */
        @DefaultValue(TrafficLabel.DEFAULT_HEADER) String headerName,
        /** 目标流量标签无实例时是否回落到 default 流量标签 */
        @DefaultValue("true") boolean fallbackToDefault) {
}

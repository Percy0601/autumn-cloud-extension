package com.autumn.cloud.extension.core.trafficlabel;

/**
 * 流量标签（trafficLabel）相关常量。
 *
 * <p>流量标签是流量隔离的最小单位：实例注册时通过 metadata 携带自己的流量标签，
 * 网关侧通过 HTTP header 指定目标流量标签，实现灰度 / 多环境流量切分。</p>
 */
public final class TrafficLabel {

    /** 实例 metadata 中的流量标签 key。 */
    public static final String META_KEY = "traffic-label";

    /** 网关侧传递流量标签标识的默认 HTTP header。 */
    public static final String DEFAULT_HEADER = "X-Autumn-Traffic-Label";

    /** 缺省流量标签：未打标的实例与未指定流量标签的请求都归属 default 流量标签。 */
    public static final String DEFAULT_LABEL = "default";

    private TrafficLabel() {
    }
}

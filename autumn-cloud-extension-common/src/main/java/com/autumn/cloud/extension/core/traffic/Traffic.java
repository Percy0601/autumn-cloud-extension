package com.autumn.cloud.extension.core.traffic;

/**
 * 流量（traffic）相关常量。
 *
 * <p>流量是流量隔离的最小单位：实例注册时通过 metadata 携带自己的流量标识，
 * 网关侧通过 HTTP header 指定目标流量，实现灰度 / 多环境流量切分。</p>
 */
public final class Traffic {

    /** 实例 metadata 中的流量 key。 */
    public static final String META_KEY = "traffic";

    /** 网关侧传递流量标识的默认 HTTP header。 */
    public static final String DEFAULT_HEADER = "X-Traffic";

    /** 缺省流量：未打标的实例与未指定流量的请求都归属 default 主干流量。 */
    public static final String DEFAULT = "default";

    private Traffic() {
    }
}

package xyz.wewin.autumn.cloud.extension.propagation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import xyz.wewin.autumn.cloud.extension.core.traffic.Traffic;

import java.util.List;

/**
 * header 透传配置（前缀 {@code autumn.propagation}）。
 *
 * <p>默认关闭：必须显式设置 {@code autumn.propagation.enabled=true} 才会真正透传。
 * 开关在每次调用时读取，配置中心刷新后立即生效，无需重启。</p>
 *
 * @param enabled            是否开启透传
 * @param mode               筛选模式，默认白名单
 * @param include            白名单，默认只带泳道 header
 * @param exclude            额外排除项（仅黑名单模式需要）
 * @param maxHeaders         单次调用最多透传多少个 header，0 表示不限制
 * @param allowTracingHeaders 是否允许透传链路追踪 header（默认否，详见文档）
 */
@ConfigurationProperties(prefix = "autumn.propagation")
public record HeaderPropagationProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("WHITELIST") PropagationMode mode,
        @DefaultValue(Traffic.DEFAULT_HEADER) List<String> include,
        @DefaultValue List<String> exclude,
        @DefaultValue("32") int maxHeaders,
        @DefaultValue("false") boolean allowTracingHeaders) {

    public HeaderPropagationProperties {
        include = include == null ? List.of() : List.copyOf(include);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
        maxHeaders = Math.max(maxHeaders, 0);
    }
}

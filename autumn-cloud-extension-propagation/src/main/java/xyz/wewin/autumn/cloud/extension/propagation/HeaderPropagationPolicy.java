package xyz.wewin.autumn.cloud.extension.propagation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 透传策略：把「配了什么」变成「这个 header 到底传不传」的纯函数判断。
 *
 * <p>判定顺序（靠前的优先，前三条不可被配置绕过）：</p>
 * <ol>
 *   <li>{@link #ALWAYS_BLOCKED_HEADERS}：传输层 / 逐跳 / Cookie 类，永不透传；</li>
 *   <li>{@link #TRACING_HEADERS}：链路追踪 header，默认永不透传（除非开启
 *       {@code allow-tracing-headers}）；</li>
 *   <li>显式 {@code include}：命中即通过（可以对冲 {@code exclude}）；</li>
 *   <li>显式 {@code exclude}：命中即拒绝；</li>
 *   <li>模式：白名单要求命中 {@code include}，黑名单默认全部通过。</li>
 * </ol>
 *
 * <p>该类不依赖 Spring，可直接单元测试。</p>
 */
public final class HeaderPropagationPolicy {

    /**
     * 永不透传：HTTP 帧控制 / 逐跳 header，复制过去会破坏下游的报文语义
     * （典型症状是 request smuggling、长度不匹配、连接被复用出错）；
     * 以及 Cookie 类，跨服务复制会话凭据属于越权风险。
     */
    public static final Set<String> ALWAYS_BLOCKED_HEADERS = Set.of(
            "host", "content-length", "transfer-encoding", "connection", "keep-alive",
            "te", "trailer", "upgrade", "expect", "proxy-connection",
            "proxy-authenticate", "proxy-authorization",
            "cookie", "set-cookie");

    /**
     * 链路追踪 header：由 tracing 中间件（Micrometer Tracing / OTel / Brave）
     * 按当前 span 重新生成，原样复制会造成父子关系错乱、span 重复、采样标记漂移。
     */
    public static final Set<String> TRACING_HEADERS = Set.of(
            // W3C Trace Context
            "traceparent", "tracestate", "baggage",
            // B3（Brave，Jaeger / Zipkin 生态）
            "b3", "x-b3-traceid", "x-b3-spanid", "x-b3-parentspanid",
            "x-b3-sampled", "x-b3-flags",
            // Jaeger 原生 / OpenTracing
            "uber-trace-id", "jaeger-debug-id", "x-ot-span-context",
            // SkyWalking
            "sw8", "sw8-correlation",
            // Elastic APM
            "elastic-apm-traceparent");

    private final boolean enabled;
    private final PropagationMode mode;
    private final Set<String> include;
    private final Set<String> exclude;
    private final int maxHeaders;
    private final boolean allowTracingHeaders;

    private HeaderPropagationPolicy(boolean enabled, PropagationMode mode, Set<String> include,
                                    Set<String> exclude, int maxHeaders, boolean allowTracingHeaders) {
        this.enabled = enabled;
        this.mode = mode;
        this.include = include;
        this.exclude = exclude;
        this.maxHeaders = maxHeaders;
        this.allowTracingHeaders = allowTracingHeaders;
    }

    public static HeaderPropagationPolicy from(HeaderPropagationProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        return new HeaderPropagationPolicy(
                properties.enabled(),
                properties.mode() == null ? PropagationMode.WHITELIST : properties.mode(),
                normalize(properties.include()),
                normalize(properties.exclude()),
                properties.maxHeaders(),
                properties.allowTracingHeaders());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PropagationMode mode() {
        return mode;
    }

    public int maxHeaders() {
        return maxHeaders;
    }

    /** 单个 header 是否允许透传。header 名大小写不敏感。 */
    public boolean allows(String name) {
        if (!enabled || name == null || name.isBlank()) {
            return false;
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (ALWAYS_BLOCKED_HEADERS.contains(key)) {
            return false;
        }
        if (!allowTracingHeaders && TRACING_HEADERS.contains(key)) {
            return false;
        }
        if (include.contains(key)) {
            return true;
        }
        if (exclude.contains(key)) {
            return false;
        }
        return mode == PropagationMode.BLACKLIST;
    }

    /**
     * 从「header 名集合」里挑出要透传的项，按出现顺序返回。
     *
     * @param names         入站 header 名
     * @param alreadyPresent 出站请求上已有的 header 名（已有的一律不覆盖）
     */
    public List<String> select(Collection<String> names, Set<String> alreadyPresent) {
        if (!enabled || names == null || names.isEmpty()) {
            return List.of();
        }
        List<String> selected = new ArrayList<>();
        for (String name : names) {
            if (!allows(name) || containsIgnoreCase(alreadyPresent, name)) {
                continue;
            }
            if (maxHeaders > 0 && selected.size() >= maxHeaders) {
                break;
            }
            selected.add(name);
        }
        return selected;
    }

    /**
     * 从「header 名 -> 值」映射里挑出要透传的项；值为空的不参与透传。
     */
    public List<String> select(Map<String, ? extends Collection<String>> source, Set<String> alreadyPresent) {
        if (!enabled || source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, ? extends Collection<String>> entry : source.entrySet()) {
            String name = entry.getKey();
            Collection<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            if (!allows(name) || containsIgnoreCase(alreadyPresent, name)) {
                continue;
            }
            if (maxHeaders > 0 && selected.size() >= maxHeaders) {
                break;
            }
            selected.add(name);
        }
        return selected;
    }

    private static Set<String> normalize(Collection<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(normalized);
    }

    private static boolean containsIgnoreCase(Set<String> names, String name) {
        if (names == null || names.isEmpty() || name == null) {
            return false;
        }
        if (names.contains(name)) {
            return true;
        }
        for (String candidate : names) {
            if (candidate != null && candidate.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}

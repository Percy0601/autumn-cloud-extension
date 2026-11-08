package xyz.wewin.autumn.cloud.extension.propagation;

import org.springframework.http.HttpHeaders;
import org.springframework.web.service.invoker.HttpRequestValues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 透传入口：持有配置与策略，产出可以挂到客户端上的扩展点。
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * @Bean
 * HeaderPropagation headerPropagation(HeaderPropagationProperties properties) {
 *     return HeaderPropagation.of(() -> properties);   // 用 supplier 支持配置热更新
 * }
 * }</pre>
 *
 * <p>策略会随配置实例变化重建，因此配置中心刷新（Consul blocking query / Nacos 长轮询）
 * 之后开关与白名单立即生效，不需要重启。</p>
 */
public final class HeaderPropagation {

    private final Supplier<HeaderPropagationProperties> propertiesSupplier;

    private volatile HeaderPropagationPolicy cachedPolicy;
    private volatile HeaderPropagationProperties cachedProperties;

    private HeaderPropagation(Supplier<HeaderPropagationProperties> propertiesSupplier) {
        this.propertiesSupplier = Objects.requireNonNull(propertiesSupplier, "propertiesSupplier must not be null");
    }

    public static HeaderPropagation of(HeaderPropagationProperties properties) {
        HeaderPropagationProperties fixed = Objects.requireNonNull(properties, "properties must not be null");
        return new HeaderPropagation(() -> fixed);
    }

    /**
     * 支持热更新的构造方式：每次调用都会重新取配置，配置实例变化时重建策略。
     */
    public static HeaderPropagation of(Supplier<HeaderPropagationProperties> propertiesSupplier) {
        return new HeaderPropagation(propertiesSupplier);
    }

    public HeaderPropagationProperties properties() {
        return propertiesSupplier.get();
    }

    /** 当前生效的策略（配置变化后自动重建）。 */
    public HeaderPropagationPolicy policy() {
        HeaderPropagationProperties current = propertiesSupplier.get();
        HeaderPropagationPolicy policy = this.cachedPolicy;
        if (policy != null && current == this.cachedProperties) {
            return policy;
        }
        policy = HeaderPropagationPolicy.from(current);
        this.cachedProperties = current;
        this.cachedPolicy = policy;
        return policy;
    }

    /** servlet（Spring MVC）场景：挂到 {@code HttpServiceProxyFactory.Builder#httpRequestValuesProcessor}。 */
    public HttpRequestValues.Processor processor() {
        return new ServletHeaderPropagationProcessor(this);
    }

    /**
     * 手工透传（不适合挂扩展点的场景，例如自研 HTTP 客户端、非请求线程里已经拿到入站 header）。
     *
     * <p>不覆盖 {@code target} 上已有的 header。</p>
     */
    public void apply(HttpHeaders target, Map<String, ? extends Collection<String>> source) {
        Objects.requireNonNull(target, "target must not be null");
        HeaderPropagationPolicy policy = policy();
        for (String name : policy.select(source, target.headerNames())) {
            Collection<String> values = source.get(name);
            if (values != null && !values.isEmpty()) {
                target.put(name, new ArrayList<>(values));
            }
        }
    }

    /** 调试用：返回本次会透传的 header 名（不涉及实际值）。 */
    public List<String> preview(Collection<String> inboundNames, Collection<String> presetNames) {
        Set<String> preset = presetNames == null ? Set.of() : new LinkedHashSet<>(presetNames);
        return policy().select(inboundNames, preset);
    }
}

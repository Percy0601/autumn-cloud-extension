package com.autumn.cloud.extension.core.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 后端选择校验（纯 Java，无 Spring 依赖）。
 *
 * <p>由各 starter 中的无条件自动装配在 BeanFactory 后置处理阶段调用，用来避免
 * 「backend 拼错 / 漏引对应 starter 时，自动装配静默不生效」的问题。</p>
 */
public final class BackendValidator {

    public static final String DISCOVERY_BACKEND = "autumn.discovery.backend";
    public static final String CONFIG_BACKEND = "autumn.config.backend";

    private static final System.Logger LOG = System.getLogger(BackendValidator.class.getName());

    private static final Map<String, String> DISCOVERY_MARKERS = markers(
            "consul", "com.autumn.cloud.extension.discovery.consul.ConsulAutoConfiguration",
            "nacos", "com.autumn.cloud.extension.discovery.nacos.NacosAutoConfiguration",
            "multicast", "com.autumn.cloud.extension.discovery.multicast.MulticastAutoConfiguration");

    private static final Map<String, String> CONFIG_MARKERS = markers(
            "consul", "com.autumn.cloud.extension.config.consul.ConsulConfigAutoConfiguration",
            "nacos", "com.autumn.cloud.extension.config.nacos.NacosConfigAutoConfiguration");

    private BackendValidator() {
    }

    /**
     * 校验服务发现后端：未配置时允许自动激活（单 starter 场景），
     * 多个 discovery starter 共存时必须显式指定 backend。
     */
    public static void validateDiscovery(String backend, ClassLoader classLoader) {
        validateDiscovery(backend, marker -> isPresent(marker, classLoader));
    }

    public static void validateDiscovery(String backend, Predicate<String> markerPresent) {
        validate("服务发现", DISCOVERY_BACKEND, backend, DISCOVERY_MARKERS, markerPresent, false);
    }

    /**
     * 校验配置中心后端：backend 显式指定才启用，未配置时给出明确提示，拼错时快速失败。
     */
    public static void validateConfig(String backend, ClassLoader classLoader) {
        validateConfig(backend, marker -> isPresent(marker, classLoader));
    }

    public static void validateConfig(String backend, Predicate<String> markerPresent) {
        validate("配置中心", CONFIG_BACKEND, backend, CONFIG_MARKERS, markerPresent, true);
    }

    private static void validate(String kind, String key, String backend,
                                 Map<String, String> markers, Predicate<String> markerPresent,
                                 boolean warnWhenMissing) {
        long present = markers.values().stream().filter(markerPresent).count();
        if (present == 0) {
            return;
        }
        if (backend == null || backend.isBlank()) {
            if (present > 1 && !warnWhenMissing) {
                throw new IllegalStateException("检测到多个" + kind + " starter 同时存在，必须显式配置 "
                        + key + "（可选值：" + String.join(" / ", markers.keySet()) + "），否则后端选择不明确。");
            }
            if (warnWhenMissing && present == 1) {
                String available = markers.entrySet().stream()
                        .filter(entry -> markerPresent.test(entry.getValue()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("");
                LOG.log(System.Logger.Level.WARNING, "未配置 " + key + "，" + kind
                        + "未启用；如需启用请设置 " + key + "=" + available + "。");
            }
            return;
        }

        String normalized = backend.trim().toLowerCase(Locale.ROOT);
        String marker = markers.get(normalized);
        if (marker == null) {
            throw new IllegalStateException(key + "=" + backend + " 不是合法值，可选值："
                    + String.join(" / ", markers.keySet()) + "。请检查拼写，或移除未使用的 starter。");
        }
        if (!markerPresent.test(marker)) {
            throw new IllegalStateException("已配置 " + key + "=" + backend
                    + "，但 classpath 上没有对应的 " + kind + " starter（缺少 " + marker
                    + "），请引入对应依赖或修正 " + key + "。");
        }
    }

    private static boolean isPresent(String className, ClassLoader classLoader) {
        ClassLoader loader = classLoader != null ? classLoader : BackendValidator.class.getClassLoader();
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static Map<String, String> markers(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(result);
    }
}

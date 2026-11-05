package com.autumn.cloud.extension.config.nacos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.env.Environment;

/**
 * 配置中心通用配置（前缀 {@code autumn.config}）。
 *
 * <p>后端专属配置见 {@link NacosConfigProperties}。</p>
 */
@ConfigurationProperties(prefix = "autumn.config")
public record AutumnConfigProperties(
        @DefaultValue("true") boolean enabled,
        String backend,
        @DefaultValue("${spring.application.name:application}.properties") String dataId,
        String group,
        String defaultGroup,
        String defaultNamespace,
        @DefaultValue("true") boolean refreshEnabled) {

    /** 解析 dataId 中的占位符（例如 ${spring.application.name}）。 */
    public String resolvedDataId(Environment environment) {
        String resolved = environment.resolvePlaceholders(dataId);
        return (resolved == null || resolved.isBlank()) ? "application.properties" : resolved.trim();
    }

    /** group 优先于 default-group，最后回落到后端专属默认分组。 */
    public String resolvedGroup(String backendDefaultGroup) {
        if (group != null && !group.isBlank()) {
            return group.trim();
        }
        if (defaultGroup != null && !defaultGroup.isBlank()) {
            return defaultGroup.trim();
        }
        return (backendDefaultGroup == null || backendDefaultGroup.isBlank())
                ? "DEFAULT_GROUP" : backendDefaultGroup.trim();
    }

    /** 通用命名空间覆盖后端专属值；未配置时返回后端值。 */
    public String resolvedNamespace(String backendNamespace) {
        return (defaultNamespace == null || defaultNamespace.isBlank())
                ? backendNamespace : defaultNamespace.trim();
    }
}

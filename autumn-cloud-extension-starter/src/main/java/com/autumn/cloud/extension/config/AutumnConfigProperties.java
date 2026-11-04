package com.autumn.cloud.extension.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 配置中心通用配置（三种后端共用）。
 *
 * <p>后端选择通过 {@code autumn.config.backend=consul|nacos} 二选一互斥加载。</p>
 */
@ConfigurationProperties(prefix = "autumn.config")
public record AutumnConfigProperties(
        /** 是否启用配置中心 */
        @DefaultValue("true") boolean enabled,
        /** 配置中心后端：consul / nacos */
        String backend,
        /** 是否启用配置自动刷新 */
        @DefaultValue("true") boolean refreshEnabled,
        /** 默认分组 */
        @DefaultValue("DEFAULT_GROUP") String defaultGroup,
        /** 默认命名空间 */
        @DefaultValue("public") String defaultNamespace) {
}

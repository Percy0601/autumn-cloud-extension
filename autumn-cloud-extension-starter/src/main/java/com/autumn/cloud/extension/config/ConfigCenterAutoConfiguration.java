package com.autumn.cloud.extension.config;

import com.autumn.cloud.extension.consul.ConsulConfigCenterClient;
import com.autumn.cloud.extension.consul.ConsulConfigProperties;
import com.autumn.cloud.extension.core.api.ConfigCenterClient;
import com.autumn.cloud.extension.nacos.NacosConfigCenterClient;
import com.autumn.cloud.extension.nacos.NacosConfigProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置中心自动装配。
 *
 * <p>根据 {@code autumn.config.backend} 属性选择加载 Consul 或 Nacos 配置中心客户端。</p>
 *
 * <p>配置示例：</p>
 * <pre>
 * # 启用配置中心
 * autumn.config.enabled=true
 * autumn.config.backend=nacos
 * autumn.config.refresh-enabled=true
 * 
 * # Nacos 配置
 * autumn.config.nacos.server-addr=127.0.0.1:8848
 * autumn.config.nacos.namespace=public
 * autumn.config.nacos.default-group=DEFAULT_GROUP
 * 
 * # 或 Consul 配置
 * #autumn.config.consul.host=127.0.0.1
 * #autumn.config.consul.port=8500
 * </pre>
 *
 * <p>核心能力：</p>
 * <ul>
 *     <li>兼容 Spring Cloud Config 机制：{@code @RefreshScope} + {@code @Value} 自动刷新</li>
 *     <li>配置变更监听：Nacos 长轮询 / Consul blocking query</li>
 *     <li>零 SDK 依赖：纯 HTTP API 直连</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "autumn.config", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({AutumnConfigProperties.class})
public class ConfigCenterAutoConfiguration {

    /**
     * Nacos 配置中心客户端
     */
    @Configuration
    @ConditionalOnProperty(prefix = "autumn.config", name = "backend", havingValue = "nacos")
    @EnableConfigurationProperties(NacosConfigProperties.class)
    static class NacosConfigConfiguration {

        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(ConfigCenterClient.class)
        public NacosConfigCenterClient nacosConfigCenterClient(NacosConfigProperties properties) {
            return new NacosConfigCenterClient(properties);
        }
    }

    /**
     * Consul 配置中心客户端
     */
    @Configuration
    @ConditionalOnProperty(prefix = "autumn.config", name = "backend", havingValue = "consul")
    @EnableConfigurationProperties(ConsulConfigProperties.class)
    static class ConsulConfigConfiguration {

        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(ConfigCenterClient.class)
        public ConsulConfigCenterClient consulConfigCenterClient(ConsulConfigProperties properties) {
            return new ConsulConfigCenterClient(properties);
        }
    }

    /**
     * 配置刷新监听器：监听配置中心变更并触发 Spring Cloud 上下文刷新
     */
    @Bean
    @ConditionalOnProperty(prefix = "autumn.config", name = "refresh-enabled", havingValue = "true", matchIfMissing = true)
    public ConfigRefreshListener configRefreshListener(ContextRefresher contextRefresher) {
        return new ConfigRefreshListener(contextRefresher);
    }
}

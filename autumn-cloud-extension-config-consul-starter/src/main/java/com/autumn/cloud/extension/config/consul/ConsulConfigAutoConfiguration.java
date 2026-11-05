package com.autumn.cloud.extension.config.consul;

import com.autumn.cloud.extension.core.api.ConfigCenterClient;
import com.autumn.cloud.extension.core.config.ConfigSources;
import com.autumn.cloud.extension.core.consul.ConsulConfigCenterClient;
import com.autumn.cloud.extension.core.consul.ConsulConfigSettings;
import com.autumn.cloud.extension.core.http.JsonHttp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

@AutoConfiguration
@ConditionalOnProperty(prefix = "autumn.config", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "autumn.config", name = "backend", havingValue = "consul")
@EnableConfigurationProperties({AutumnConfigProperties.class, ConsulConfigProperties.class})
public class ConsulConfigAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConsulConfigAutoConfiguration.class);

    /**
     * 在任何单例 Bean 创建之前把远程配置写入 Environment，让 {@code @Value} 能直接读到。
     * 远程不可达时只告警并继续使用本地配置，不阻断启动。
     */
    @Bean
    static BeanFactoryPostProcessor consulConfigPropertySourceInitializer(Environment environment) {
        return beanFactory -> {
            if (!(environment instanceof ConfigurableEnvironment configurable)) {
                return;
            }
            AutumnConfigProperties props = binderProps(environment);
            if (!props.enabled()) {
                return;
            }
            ConsulConfigProperties backend = backendProps(environment);
            String dataId = props.resolvedDataId(environment);
            String group = props.resolvedGroup(backend.defaultGroup());
            try (ConfigCenterClient client = createClient(props, backend, 3000)) {
                String content = client.getConfig(dataId, group, 3000);
                Map<String, Object> values = ConfigSources.parse(content);
                configurable.getPropertySources().addFirst(
                        new MapPropertySource(ConfigSources.PROPERTY_SOURCE_NAME, values));
                log.info("已从 Consul 加载远程配置 {}/{}，共 {} 项", group, dataId, values.size());
            } catch (Exception e) {
                log.warn("从 Consul 加载远程配置 {}/{} 失败，继续使用本地配置: {}", group, dataId, e.toString());
            }
        };
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ConfigCenterClient.class)
    public ConsulConfigCenterClient consulConfigCenterClient(AutumnConfigProperties props,
                                                             ConsulConfigProperties backend) {
        // blocking query 最长 30s，读超时留出余量
        return createClient(props, backend, 40_000);
    }

    /**
     * 注册配置变更监听：更新 Environment 中的 PropertySource，再触发 @RefreshScope 刷新。
     */
    @Bean
    @ConditionalOnProperty(prefix = "autumn.config", name = "refresh-enabled", havingValue = "true", matchIfMissing = true)
    public ConfigRefreshListener consulConfigRefreshListener(ConfigCenterClient client,
                                                             AutumnConfigProperties props,
                                                             ConsulConfigProperties backend,
                                                             Environment environment,
                                                             ContextRefresher contextRefresher) {
        ConfigRefreshListener listener = new ConfigRefreshListener(
                (ConfigurableEnvironment) environment, contextRefresher);
        if (props.refreshEnabled()) {
            String dataId = props.resolvedDataId(environment);
            String group = props.resolvedGroup(backend.defaultGroup());
            client.addListener(dataId, group, listener);
            log.info("已注册 Consul 配置变更监听 {}/{}", group, dataId);
        }
        return listener;
    }

    private static ConsulConfigCenterClient createClient(AutumnConfigProperties props,
                                                         ConsulConfigProperties p, int requestTimeoutMs) {
        ConsulConfigSettings settings = new ConsulConfigSettings(
                p.host(), p.port(), p.scheme(), p.token(),
                props.resolvedDatacenter(p.datacenter()), props.resolvedGroup(p.defaultGroup()));
        return new ConsulConfigCenterClient(settings, new JsonHttp(3000, requestTimeoutMs));
    }

    private static AutumnConfigProperties binderProps(Environment environment) {
        return Binder.get(environment)
                .bind("autumn.config", AutumnConfigProperties.class)
                .orElseGet(() -> new AutumnConfigProperties(true, null,
                        "${spring.application.name:application}.properties", null, null, null, true));
    }

    private static ConsulConfigProperties backendProps(Environment environment) {
        return Binder.get(environment)
                .bind("autumn.config.consul", ConsulConfigProperties.class)
                .orElseGet(() -> new ConsulConfigProperties(null, 0, null, null, null, null));
    }
}

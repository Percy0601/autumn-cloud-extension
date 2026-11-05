package com.autumn.cloud.extension.config.nacos;

import com.autumn.cloud.extension.core.api.ConfigCenterClient;
import com.autumn.cloud.extension.core.config.ConfigSources;
import com.autumn.cloud.extension.core.http.JsonHttp;
import com.autumn.cloud.extension.core.nacos.NacosConfigCenterClient;
import com.autumn.cloud.extension.core.nacos.NacosConfigSettings;
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
@ConditionalOnProperty(prefix = "autumn.config", name = "backend", havingValue = "nacos")
@EnableConfigurationProperties({AutumnConfigProperties.class, NacosConfigProperties.class})
public class NacosConfigAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigAutoConfiguration.class);

    /**
     * 在任何单例 Bean 创建之前把远程配置写入 Environment，让 {@code @Value} 能直接读到。
     * 远程不可达时只告警并继续使用本地配置，不阻断启动。
     */
    @Bean
    static BeanFactoryPostProcessor nacosConfigPropertySourceInitializer(Environment environment) {
        return beanFactory -> {
            if (!(environment instanceof ConfigurableEnvironment configurable)) {
                return;
            }
            AutumnConfigProperties props = binderProps(environment);
            if (!props.enabled()) {
                return;
            }
            NacosConfigProperties backend = backendProps(environment);
            String dataId = props.resolvedDataId(environment);
            String group = props.resolvedGroup(backend.defaultGroup());
            try (ConfigCenterClient client = createClient(props, backend, 3000)) {
                String content = client.getConfig(dataId, group, 3000);
                Map<String, Object> values = ConfigSources.parse(content);
                configurable.getPropertySources().addFirst(
                        new MapPropertySource(ConfigSources.PROPERTY_SOURCE_NAME, values));
                log.info("已从 Nacos 加载远程配置 {}/{}，共 {} 项", group, dataId, values.size());
            } catch (Exception e) {
                log.warn("从 Nacos 加载远程配置 {}/{} 失败，继续使用本地配置: {}", group, dataId, e.toString());
            }
        };
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ConfigCenterClient.class)
    public NacosConfigCenterClient nacosConfigCenterClient(AutumnConfigProperties props,
                                                           NacosConfigProperties backend) {
        // 长轮询需要比服务端 hold 时间更长的读超时，否则会退化成频繁超时重试
        int timeoutMs = (int) Math.min(120_000L, Math.max(60_000L, backend.pollingTimeoutMs() + 10_000L));
        return createClient(props, backend, timeoutMs);
    }

    /**
     * 注册配置变更监听：更新 Environment 中的 PropertySource，再触发 @RefreshScope 刷新。
     */
    @Bean
    @ConditionalOnProperty(prefix = "autumn.config", name = "refresh-enabled", havingValue = "true", matchIfMissing = true)
    public ConfigRefreshListener nacosConfigRefreshListener(ConfigCenterClient client,
                                                            AutumnConfigProperties props,
                                                            NacosConfigProperties backend,
                                                            Environment environment,
                                                            ContextRefresher contextRefresher) {
        ConfigRefreshListener listener = new ConfigRefreshListener(
                (ConfigurableEnvironment) environment, contextRefresher);
        if (props.refreshEnabled()) {
            String dataId = props.resolvedDataId(environment);
            String group = props.resolvedGroup(backend.defaultGroup());
            client.addListener(dataId, group, listener);
            log.info("已注册 Nacos 配置变更监听 {}/{}", group, dataId);
        }
        return listener;
    }

    private static NacosConfigCenterClient createClient(AutumnConfigProperties props,
                                                        NacosConfigProperties p, int requestTimeoutMs) {
        NacosConfigSettings settings = new NacosConfigSettings(
                p.serverAddr(), props.resolvedNamespace(p.namespace()),
                props.resolvedGroup(p.defaultGroup()), p.accessToken(), p.pollingTimeoutMs());
        return new NacosConfigCenterClient(settings, new JsonHttp(3000, requestTimeoutMs));
    }

    private static AutumnConfigProperties binderProps(Environment environment) {
        return Binder.get(environment)
                .bind("autumn.config", AutumnConfigProperties.class)
                .orElseGet(() -> new AutumnConfigProperties(true, null,
                        "${spring.application.name:application}.properties", null, null, null, true));
    }

    private static NacosConfigProperties backendProps(Environment environment) {
        return Binder.get(environment)
                .bind("autumn.config.nacos", NacosConfigProperties.class)
                .orElseGet(() -> new NacosConfigProperties(null, null, null, null, 0));
    }
}

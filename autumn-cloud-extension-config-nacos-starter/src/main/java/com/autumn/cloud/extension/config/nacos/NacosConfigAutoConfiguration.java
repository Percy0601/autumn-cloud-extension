package com.autumn.cloud.extension.config.nacos;

import com.autumn.cloud.extension.core.api.ConfigCenterClient;
import com.autumn.cloud.extension.core.nacos.NacosConfigCenterClient;
import com.autumn.cloud.extension.core.nacos.NacosConfigSettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "autumn.config", name = "backend", havingValue = "nacos")
@EnableConfigurationProperties(NacosConfigProperties.class)
public class NacosConfigAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ConfigCenterClient.class)
    public NacosConfigCenterClient nacosConfigCenterClient(NacosConfigProperties properties) {
        return new NacosConfigCenterClient(toSettings(properties));
    }

    @Bean
    @ConditionalOnProperty(prefix = "autumn.config", name = "refresh-enabled", havingValue = "true", matchIfMissing = true)
    public ConfigRefreshListener configRefreshListener(ContextRefresher contextRefresher) {
        return new ConfigRefreshListener(contextRefresher);
    }

    private static NacosConfigSettings toSettings(NacosConfigProperties p) {
        return new NacosConfigSettings(p.serverAddr(), p.namespace(), p.defaultGroup(), p.accessToken(), p.pollingTimeoutMs());
    }
}

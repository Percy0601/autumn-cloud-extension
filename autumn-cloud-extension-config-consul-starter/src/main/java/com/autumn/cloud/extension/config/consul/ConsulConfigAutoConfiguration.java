package com.autumn.cloud.extension.config.consul;

import com.autumn.cloud.extension.core.api.ConfigCenterClient;
import com.autumn.cloud.extension.core.consul.ConsulConfigCenterClient;
import com.autumn.cloud.extension.core.consul.ConsulConfigSettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "autumn.config", name = "backend", havingValue = "consul")
@EnableConfigurationProperties(ConsulConfigProperties.class)
public class ConsulConfigAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ConfigCenterClient.class)
    public ConsulConfigCenterClient consulConfigCenterClient(ConsulConfigProperties properties) {
        return new ConsulConfigCenterClient(toSettings(properties));
    }

    @Bean
    @ConditionalOnProperty(prefix = "autumn.config", name = "refresh-enabled", havingValue = "true", matchIfMissing = true)
    public ConfigRefreshListener configRefreshListener(ContextRefresher contextRefresher) {
        return new ConfigRefreshListener(contextRefresher);
    }

    private static ConsulConfigSettings toSettings(ConsulConfigProperties p) {
        return new ConsulConfigSettings(p.host(), p.port(), p.scheme(), p.token(), p.datacenter(), p.defaultGroup());
    }
}

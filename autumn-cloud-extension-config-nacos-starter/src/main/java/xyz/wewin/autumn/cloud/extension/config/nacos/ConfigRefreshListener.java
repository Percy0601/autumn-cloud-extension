package xyz.wewin.autumn.cloud.extension.config.nacos;

import xyz.wewin.autumn.cloud.extension.core.config.ConfigSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 配置变更回调：原地更新 Environment 中的远程配置 PropertySource，再触发
 * {@link ContextRefresher#refresh()}，使 {@code @RefreshScope} Bean 重新绑定 {@code @Value}。
 *
 * <p>与 {@code ConfigCenterClient.addListener(dataId, group, Consumer<String>)} 的签名一致。</p>
 */
public class ConfigRefreshListener implements Consumer<String> {

    private static final Logger log = LoggerFactory.getLogger(ConfigRefreshListener.class);

    private final ConfigurableEnvironment environment;
    private final ContextRefresher contextRefresher;
    private volatile String lastContent;

    public ConfigRefreshListener(ConfigurableEnvironment environment, ContextRefresher contextRefresher) {
        this.environment = environment;
        this.contextRefresher = contextRefresher;
    }

    @Override
    public void accept(String content) {
        if (Objects.equals(content, lastContent)) {
            return;
        }
        lastContent = content;
        Map<String, Object> parsed;
        try {
            parsed = ConfigSources.parse(content);
        } catch (IllegalArgumentException e) {
            log.warn("远程配置内容无法解析，本次刷新跳过: {}", e.getMessage());
            return;
        }

        MutablePropertySources sources = environment.getPropertySources();
        PropertySource<?> existing = sources.get(ConfigSources.PROPERTY_SOURCE_NAME);
        if (existing instanceof MapPropertySource mapSource) {
            Map<String, Object> source = mapSource.getSource();
            source.clear();
            source.putAll(parsed);
        } else {
            sources.addFirst(new MapPropertySource(ConfigSources.PROPERTY_SOURCE_NAME,
                    new LinkedHashMap<>(parsed)));
        }

        log.info("远程配置已更新，共 {} 项，触发 Spring 上下文刷新", parsed.size());
        try {
            contextRefresher.refresh();
        } catch (Exception e) {
            log.error("刷新 Spring 上下文失败", e);
        }
    }
}

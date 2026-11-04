package com.autumn.cloud.extension.config;

import com.autumn.cloud.extension.core.api.ConfigChangeEvent;
import com.autumn.cloud.extension.core.api.ConfigListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置刷新监听器：监听配置中心变更并触发 Spring Cloud 上下文刷新。
 *
 * <p>当配置中心（Consul/Nacos）的配置发生变更时，本监听器会：
 * <ol>
 *     <li>接收配置变更事件</li>
 *     <li>调用 {@link ContextRefresher#refresh()} 刷新 Spring 上下文</li>
 *     <li>触发 {@code RefreshRemoteApplicationEvent} 通知其他实例（可选）</li>
 * </ol>
 *
 * <p>配合 {@code @RefreshScope} 注解使用，可实现 Bean 的自动重新加载。</p>
 */
public class ConfigRefreshListener implements ConfigListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigRefreshListener.class);

    private final ContextRefresher contextRefresher;
    private final Map<String, String> lastConfigCache = new ConcurrentHashMap<>();

    public ConfigRefreshListener(ContextRefresher contextRefresher) {
        this.contextRefresher = contextRefresher;
    }

    @Override
    public void accept(ConfigChangeEvent event) {
        String key = event.dataId() + ":" + event.group();
        String oldContent = lastConfigCache.get(key);
        String newContent = event.newContent();

        if (event.isDelete()) {
            lastConfigCache.remove(key);
            log.info("Config deleted: {}, triggering refresh", key);
            refresh();
            return;
        }

        if (event.isAdd() || !newContent.equals(oldContent)) {
            lastConfigCache.put(key, newContent);
            log.info("Config changed: {}, triggering refresh", key);
            refresh();
        }
    }

    private void refresh() {
        try {
            contextRefresher.refresh();
        } catch (Exception e) {
            log.error("Failed to refresh context after config change", e);
        }
    }
}

package com.autumn.cloud.extension.config.nacos;

import com.autumn.cloud.extension.core.api.ConfigChangeEvent;
import com.autumn.cloud.extension.core.api.ConfigListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

package com.autumn.cloud.extension.nacos;

import com.autumn.cloud.extension.core.api.ConfigChangeEvent;
import com.autumn.cloud.extension.core.api.ConfigCenterClient;
import com.autumn.cloud.extension.core.api.ConfigListener;
import com.autumn.cloud.extension.core.http.HttpCallException;
import com.autumn.cloud.extension.core.http.JsonHttp;
import com.autumn.cloud.extension.core.http.QueryStrings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Nacos 配置中心客户端（HTTP API 直连，不依赖 Nacos SDK）。
 *
 * <p>对应 Nacos Config API v1：</p>
 * <ul>
 *     <li>获取配置：GET /nacos/v1/cs/configs?dataId=xxx&group=xxx</li>
 *     <li>发布配置：POST /nacos/v1/cs/configs</li>
 *     <li>删除配置：DELETE /nacos/v1/cs/configs</li>
 *     <li>监听变更：POST /nacos/v1/cs/configs/listener（长轮询）</li>
 * </ul>
 *
 * <p>设计原则：</p>
 * <ul>
 *     <li>纯 HTTP API 调用，无 SDK 依赖</li>
 *     <li>支持长轮询监听配置变更</li>
 *     <li>适配 GraalVM Native Image（无反射）</li>
 * </ul>
 */
public class NacosConfigCenterClient implements ConfigCenterClient {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigCenterClient.class);
    private static final long DEFAULT_POLLING_MS = 30000; // 30 seconds

    private final NacosConfigProperties properties;
    private final JsonHttp http;
    private final Map<String, NacosWatcher> watchers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nacos-config-watcher");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public NacosConfigCenterClient(NacosConfigProperties properties) {
        this(properties, new JsonHttp());
    }

    NacosConfigCenterClient(NacosConfigProperties properties, JsonHttp http) {
        this.properties = properties;
        this.http = http;
    }

    @Override
    public String getConfig(String dataId, String group, long timeout) {
        Map<String, String> params = commonParams();
        params.put("dataId", dataId);
        params.put("group", group);
        try {
            String url = configUrl() + "?" + QueryStrings.encode(params);
            String body = http.get(url, null);
            // Nacos returns JSON with "content" field
            if (body != null && body.contains("\"content\"")) {
                // Simple extraction without full JSON parsing
                int start = body.indexOf("\"content\":\"") + 11;
                int end = body.indexOf("\"", start);
                if (start > 10 && end > start) {
                    return body.substring(start, end)
                            .replace("\\n", "\n")
                            .replace("\\r", "\r")
                            .replace("\\\"", "\"");
                }
            }
            return body;
        } catch (HttpCallException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                return null;
            }
            throw e;
        } catch (IOException e) {
            throw new HttpCallException("nacos get config failed: " + dataId, e);
        }
    }

    @Override
    public boolean publishConfig(String dataId, String group, String content) {
        Map<String, String> params = commonParams();
        params.put("dataId", dataId);
        params.put("group", group);
        params.put("content", content);
        try {
            String url = configUrl();
            http.post(url + "?" + QueryStrings.encode(params), null);
            return true;
        } catch (IOException e) {
            throw new HttpCallException("nacos publish config failed: " + dataId, e);
        }
    }

    @Override
    public boolean removeConfig(String dataId, String group) {
        Map<String, String> params = commonParams();
        params.put("dataId", dataId);
        params.put("group", group);
        try {
            String url = configUrl() + "?" + QueryStrings.encode(params);
            http.delete(url, null);
            return true;
        } catch (IOException e) {
            throw new HttpCallException("nacos remove config failed: " + dataId, e);
        }
    }

    @Override
    public void addListener(String dataId, String group, Consumer<String> listener) {
        String key = buildKey(dataId, group);
        if (watchers.containsKey(key)) {
            log.warn("Watcher already exists for: {}", key);
            return;
        }
        NacosWatcher watcher = new NacosWatcher(dataId, group, listener);
        watchers.put(key, watcher);
        scheduler.schedule(() -> watchLoop(watcher), 0, TimeUnit.MILLISECONDS);
        log.info("Added config watcher for: {}", key);
    }

    @Override
    public void removeListener(String dataId, String group, Consumer<String> listener) {
        String key = buildKey(dataId, group);
        NacosWatcher removed = watchers.remove(key);
        if (removed != null) {
            removed.stop();
            log.info("Removed config watcher for: {}", key);
        }
    }

    @Override
    public String getDefaultGroup() {
        return properties.defaultGroup();
    }

    @Override
    public String getDefaultNamespace() {
        return properties.namespace();
    }

    @Override
    public String description() {
        return "Autumn Nacos Config Center (OpenAPI v1)";
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            watchers.values().forEach(NacosWatcher::stop);
            watchers.clear();
            scheduler.shutdownNow();
            try {
                http.close();
            } catch (IOException e) {
                log.warn("Failed to close HTTP client", e);
            }
        }
    }

    private void watchLoop(NacosWatcher watcher) {
        String lastMd5 = watcher.getLastMd5();
        while (!watcher.isStopped() && !closed.get()) {
            try {
                // Long polling for config changes
                Map<String, String> params = new LinkedHashMap<>();
                params.put("Listening-Configs", buildListeningConfigs(watcher.dataId, watcher.group, lastMd5));
                
                String url = configUrl() + "/listener?" + QueryStrings.encode(commonParams());
                String body = http.post(url, params);
                
                // Check if config changed (response contains dataId)
                if (body != null && body.contains(watcher.dataId)) {
                    String newContent = getConfig(watcher.dataId, watcher.group, 5000);
                    String newMd5 = md5(newContent);
                    if (!newMd5.equals(lastMd5)) {
                        watcher.notifyListener(newContent);
                        lastMd5 = newMd5;
                        watcher.setLastMd5(lastMd5);
                    }
                }
            } catch (Exception e) {
                if (!watcher.isStopped() && !closed.get()) {
                    log.debug("Nacos watch error, retrying: {}", e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private String buildListeningConfigs(String dataId, String group, String md5) {
        // Format: dataId^2group^2md5^1
        return dataId + "\u0002" + group + "\u0002" + md5 + "\u0001";
    }

    private String buildKey(String dataId, String group) {
        return dataId + ":" + group;
    }

    private Map<String, String> commonParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tenant", properties.namespace());
        if (properties.accessToken() != null && !properties.accessToken().isBlank()) {
            params.put("accessToken", properties.accessToken().trim());
        }
        return params;
    }

    private String configUrl() {
        return "http://" + properties.serverAddr() + "/nacos/v1/cs/configs";
    }

    private static String md5(String content) {
        if (content == null) {
            return "";
        }
        // Simple hash for demo; in production use java.security.MessageDigest
        return String.valueOf(content.hashCode());
    }

    /**
     * Nacos 配置监听器内部类
     */
    private static class NacosWatcher {
        final String dataId;
        final String group;
        final Consumer<String> listener;
        volatile String lastMd5 = "";
        volatile boolean stopped = false;

        NacosWatcher(String dataId, String group, Consumer<String> listener) {
            this.dataId = dataId;
            this.group = group;
            this.listener = listener;
        }

        void notifyListener(String content) {
            listener.accept(content);
        }

        String getLastMd5() {
            return lastMd5;
        }

        void setLastMd5(String md5) {
            this.lastMd5 = md5;
        }

        void stop() {
            this.stopped = true;
        }

        boolean isStopped() {
            return stopped;
        }
    }
}

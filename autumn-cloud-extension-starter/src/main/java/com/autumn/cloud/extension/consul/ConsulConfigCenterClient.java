package com.autumn.cloud.extension.consul;

import com.autumn.cloud.extension.core.api.ConfigChangeEvent;
import com.autumn.cloud.extension.core.api.ConfigCenterClient;
import com.autumn.cloud.extension.core.api.ConfigListener;
import com.autumn.cloud.extension.core.http.HttpCallException;
import com.autumn.cloud.extension.core.http.JsonHttp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consul KV 配置中心客户端（HTTP API 直连，不依赖 Consul SDK）。
 *
 * <p>对应 Consul KV API：</p>
 * <ul>
 *     <li>获取配置：GET /v1/kv/{key}?raw</li>
 *     <li>发布配置：PUT /v1/kv/{key}</li>
 *     <li>删除配置：DELETE /v1/kv/{key}</li>
 *     <li>监听变更：GET /v1/kv/{key}?index={waitIndex}&wait={waitTime}（blocking query）</li>
 * </ul>
 *
 * <p>设计原则：</p>
 * <ul>
 *     <li>纯 HTTP API 调用，无 SDK 依赖</li>
 *     <li>支持长轮询监听配置变更</li>
 *     <li>适配 GraalVM Native Image（无反射）</li>
 * </ul>
 */
public class ConsulConfigCenterClient implements ConfigCenterClient {

    private static final Logger log = LoggerFactory.getLogger(ConsulConfigCenterClient.class);
    private static final long DEFAULT_WAIT_MS = 300000; // 5 minutes

    private final ConsulConfigProperties properties;
    private final JsonHttp http;
    private final Map<String, ConsulWatcher> watchers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "consul-config-watcher");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ConsulConfigCenterClient(ConsulConfigProperties properties) {
        this(properties, new JsonHttp());
    }

    ConsulConfigCenterClient(ConsulConfigProperties properties, JsonHttp http) {
        this.properties = properties;
        this.http = http;
    }

    @Override
    public String getConfig(String dataId, String group, long timeout) {
        String key = buildKey(dataId, group);
        try {
            String url = kvUrl(key) + "?raw";
            return http.get(url, headers());
        } catch (HttpCallException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                return null;
            }
            throw e;
        } catch (Exception e) {
            throw new HttpCallException("consul get config failed: " + key, e);
        }
    }

    @Override
    public boolean publishConfig(String dataId, String group, String content) {
        String key = buildKey(dataId, group);
        try {
            http.put(kvUrl(key), content, headers());
            return true;
        } catch (Exception e) {
            throw new HttpCallException("consul publish config failed: " + key, e);
        }
    }

    @Override
    public boolean removeConfig(String dataId, String group) {
        String key = buildKey(dataId, group);
        try {
            http.delete(kvUrl(key), headers());
            return true;
        } catch (Exception e) {
            throw new HttpCallException("consul remove config failed: " + key, e);
        }
    }

    @Override
    public void addListener(String dataId, String group, java.util.function.Consumer<String> listener) {
        String key = buildKey(dataId, group);
        if (watchers.containsKey(key)) {
            log.warn("Watcher already exists for key: {}", key);
            return;
        }
        ConsulWatcher watcher = new ConsulWatcher(dataId, group, listener);
        watchers.put(key, watcher);
        scheduler.schedule(() -> watchLoop(watcher), 0, TimeUnit.MILLISECONDS);
        log.info("Added config watcher for: {}", key);
    }

    @Override
    public void removeListener(String dataId, String group, java.util.function.Consumer<String> listener) {
        String key = buildKey(dataId, group);
        ConsulWatcher removed = watchers.remove(key);
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
        return properties.datacenter();
    }

    @Override
    public String description() {
        return "Autumn Consul Config Center (KV API)";
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            watchers.values().forEach(ConsulWatcher::stop);
            watchers.clear();
            scheduler.shutdownNow();
            try {
                http.close();
            } catch (Exception e) {
                log.warn("Failed to close HTTP client", e);
            }
        }
    }

    private void watchLoop(ConsulWatcher watcher) {
        long waitIndex = watcher.getWaitIndex();
        while (!watcher.isStopped() && !closed.get()) {
            try {
                String key = buildKey(watcher.dataId, watcher.group);
                String url = kvUrl(key) + "?index=" + waitIndex + "&wait=" + (DEFAULT_WAIT_MS / 1000) + "s";
                String response = http.get(url, headers());
                
                // Parse X-Consul-Index header (simplified: use content hash as fallback)
                long newIndex = response == null ? 0 : response.hashCode();
                if (newIndex != waitIndex) {
                    watcher.notifyListener(response);
                    waitIndex = newIndex;
                    watcher.setWaitIndex(waitIndex);
                }
            } catch (Exception e) {
                if (!watcher.isStopped() && !closed.get()) {
                    log.debug("Consul watch error, retrying: {}", e.getMessage());
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

    private String buildKey(String dataId, String group) {
        return "config/" + group + "/" + dataId;
    }

    private String kvUrl(String key) {
        return baseUrl() + "/v1/kv/" + urlEncode(key.replace("/", "%2F"));
    }

    private String baseUrl() {
        return properties.scheme() + "://" + properties.host() + ":" + properties.port();
    }

    private Map<String, String> headers() {
        if (properties.token() == null || properties.token().isBlank()) {
            return null;
        }
        return Map.of("X-Consul-Token", properties.token().trim());
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Consul 配置监听器内部类
     */
    private static class ConsulWatcher {
        final String dataId;
        final String group;
        final java.util.function.Consumer<String> listener;
        volatile long waitIndex = 0;
        volatile boolean stopped = false;

        ConsulWatcher(String dataId, String group, java.util.function.Consumer<String> listener) {
            this.dataId = dataId;
            this.group = group;
            this.listener = listener;
        }

        void notifyListener(String content) {
            listener.accept(content);
        }

        long getWaitIndex() {
            return waitIndex;
        }

        void setWaitIndex(long index) {
            this.waitIndex = index;
        }

        void stop() {
            this.stopped = true;
        }

        boolean isStopped() {
            return stopped;
        }
    }
}

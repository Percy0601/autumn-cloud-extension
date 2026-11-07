package xyz.wewin.autumn.cloud.extension.core.consul;

import xyz.wewin.autumn.cloud.extension.core.api.ConfigCenterClient;
import xyz.wewin.autumn.cloud.extension.core.http.HttpCallException;
import xyz.wewin.autumn.cloud.extension.core.http.JsonHttp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Consul KV 配置中心客户端（纯 HTTP 协议实现，零 SDK 依赖）。
 *
 * <p>对应 Consul KV API：</p>
 * <ul>
 *     <li>获取配置：GET /v1/kv/{key}?raw</li>
 *     <li>发布配置：PUT /v1/kv/{key}</li>
 *     <li>删除配置：DELETE /v1/kv/{key}</li>
 *     <li>监听变更：GET /v1/kv/{key}?index={waitIndex}&wait={waitTime}（blocking query）</li>
 * </ul>
 */
public class ConsulConfigCenterClient implements ConfigCenterClient {

    private static final long DEFAULT_WAIT_MS = 30_000L;

    private final ConsulConfigSettings settings;
    private final JsonHttp http;
    private final Map<String, ConsulWatcher> watchers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "consul-config-watcher");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ConsulConfigCenterClient(ConsulConfigSettings settings) {
        this(settings, new JsonHttp());
    }

    public ConsulConfigCenterClient(ConsulConfigSettings settings, JsonHttp http) {
        this.settings = settings;
        this.http = http;
    }

    @Override
    public String getConfig(String dataId, String group, long timeout) {
        String key = buildKey(dataId, group);
        try {
            return http.get(kvUrl(key) + "?raw", headers());
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
    public void addListener(String dataId, String group, Consumer<String> listener) {
        String key = buildKey(dataId, group);
        if (watchers.containsKey(key)) {
            return;
        }
        ConsulWatcher watcher = new ConsulWatcher(dataId, group, listener);
        watchers.put(key, watcher);
        scheduler.schedule(() -> watchLoop(watcher), 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public void removeListener(String dataId, String group, Consumer<String> listener) {
        String key = buildKey(dataId, group);
        ConsulWatcher removed = watchers.remove(key);
        if (removed != null) {
            removed.stop();
        }
    }

    @Override
    public String getDefaultGroup() {
        return settings.defaultGroup();
    }

    @Override
    public String getDefaultNamespace() {
        return settings.datacenter();
    }

    @Override
    public String description() {
        return "Consul Config Center (KV API)";
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            watchers.values().forEach(ConsulWatcher::stop);
            watchers.clear();
            scheduler.shutdownNow();
            http.close();
        }
    }

    private void watchLoop(ConsulWatcher watcher) {
        long waitIndex = watcher.getWaitIndex();
        while (!watcher.isStopped() && !closed.get()) {
            try {
                String key = buildKey(watcher.dataId, watcher.group);
                String url = kvUrl(key) + "?index=" + waitIndex + "&wait=" + (DEFAULT_WAIT_MS / 1000) + "s";
                String response = http.get(url, headers());
                long newIndex = response == null ? 0 : response.hashCode();
                if (newIndex != waitIndex) {
                    // blocking query 返回的是 KV 元数据 JSON，监听器需要的是配置原文
                    watcher.notifyListener(getConfig(watcher.dataId, watcher.group, 5000));
                    waitIndex = newIndex;
                    watcher.setWaitIndex(waitIndex);
                }
            } catch (Exception e) {
                if (!watcher.isStopped() && !closed.get()) {
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
        return settings.scheme() + "://" + settings.host() + ":" + settings.port();
    }

    private Map<String, String> headers() {
        if (settings.token() == null || settings.token().isBlank()) {
            return null;
        }
        return Map.of("X-Consul-Token", settings.token().trim());
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static class ConsulWatcher {
        final String dataId;
        final String group;
        final Consumer<String> listener;
        volatile long waitIndex;
        volatile boolean stopped;

        ConsulWatcher(String dataId, String group, Consumer<String> listener) {
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

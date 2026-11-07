package xyz.wewin.autumn.cloud.extension.core.nacos;

import xyz.wewin.autumn.cloud.extension.core.api.ConfigCenterClient;
import xyz.wewin.autumn.cloud.extension.core.http.HttpCallException;
import xyz.wewin.autumn.cloud.extension.core.http.JsonHttp;
import xyz.wewin.autumn.cloud.extension.core.http.QueryStrings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Nacos 配置中心客户端（纯 HTTP 协议实现，零 SDK 依赖）。
 *
 * <p>对应 Nacos Config API v1：</p>
 * <ul>
 *     <li>获取配置：GET /nacos/v1/cs/configs?dataId=xxx&group=xxx</li>
 *     <li>发布配置：POST /nacos/v1/cs/configs</li>
 *     <li>删除配置：DELETE /nacos/v1/cs/configs</li>
 *     <li>监听变更：POST /nacos/v1/cs/configs/listener（长轮询）</li>
 * </ul>
 */
public class NacosConfigCenterClient implements ConfigCenterClient {

    private final NacosConfigSettings settings;
    private final JsonHttp http;
    private final Map<String, NacosWatcher> watchers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nacos-config-watcher");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public NacosConfigCenterClient(NacosConfigSettings settings) {
        this(settings, new JsonHttp());
    }

    public NacosConfigCenterClient(NacosConfigSettings settings, JsonHttp http) {
        this.settings = settings;
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
            if (body != null && body.contains("\"content\"")) {
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
        } catch (Exception e) {
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
            http.post(configUrl() + "?" + QueryStrings.encode(params), null);
            return true;
        } catch (Exception e) {
            throw new HttpCallException("nacos publish config failed: " + dataId, e);
        }
    }

    @Override
    public boolean removeConfig(String dataId, String group) {
        Map<String, String> params = commonParams();
        params.put("dataId", dataId);
        params.put("group", group);
        try {
            http.delete(configUrl() + "?" + QueryStrings.encode(params), null);
            return true;
        } catch (Exception e) {
            throw new HttpCallException("nacos remove config failed: " + dataId, e);
        }
    }

    @Override
    public void addListener(String dataId, String group, Consumer<String> listener) {
        String key = buildKey(dataId, group);
        if (watchers.containsKey(key)) {
            return;
        }
        NacosWatcher watcher = new NacosWatcher(dataId, group, listener);
        try {
            // 以当前内容为基线，避免监听器注册完成就触发一次"假变更"
            watcher.setLastMd5(md5(getConfig(dataId, group, 5000)));
        } catch (Exception e) {
            // 服务端暂不可达时保持空基线，长轮询会自行重试
        }
        watchers.put(key, watcher);
        scheduler.schedule(() -> watchLoop(watcher), 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public void removeListener(String dataId, String group, Consumer<String> listener) {
        String key = buildKey(dataId, group);
        NacosWatcher removed = watchers.remove(key);
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
        return settings.namespace();
    }

    @Override
    public String description() {
        return "Nacos Config Center (OpenAPI v1)";
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            watchers.values().forEach(NacosWatcher::stop);
            watchers.clear();
            scheduler.shutdownNow();
            http.close();
        }
    }

    private void watchLoop(NacosWatcher watcher) {
        String lastMd5 = watcher.getLastMd5();
        while (!watcher.isStopped() && !closed.get()) {
            try {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("Listening-Configs", buildListeningConfigs(watcher.dataId, watcher.group, lastMd5));
                String url = configUrl() + "/listener?" + QueryStrings.encode(commonParams());
                // Nacos 长轮询要求 Listening-Configs 走 form body（此前误当作 header 发送）
                String body = http.post(url, params,
                        Map.of("Content-Type", "application/x-www-form-urlencoded"));
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
        return dataId + "\u0002" + group + "\u0002" + md5 + "\u0001";
    }

    private String buildKey(String dataId, String group) {
        return dataId + ":" + group;
    }

    private Map<String, String> commonParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tenant", settings.namespace());
        if (settings.accessToken() != null && !settings.accessToken().isBlank()) {
            params.put("accessToken", settings.accessToken().trim());
        }
        return params;
    }

    private String configUrl() {
        return "http://" + settings.serverAddr() + "/nacos/v1/cs/configs";
    }

    /** Nacos 长轮询要求真实的 32 位 MD5，不能拿 String.hashCode 冒充。 */
    private static String md5(String content) {
        if (content == null) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 MD5 支持", e);
        }
    }

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

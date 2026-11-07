package xyz.wewin.autumn.cloud.extension.core.nacos;

/**
 * Nacos Naming 连接配置（纯 Java，无 Spring 依赖）。
 *
 * @param serverAddr      Nacos 服务端地址 host:port
 * @param namespace       命名空间
 * @param group           分组
 * @param accessToken     鉴权 Token（未开启鉴权时为 null）
 * @param beatIntervalMs  临时实例心跳间隔（毫秒）
 */
public record NacosSettings(
        String serverAddr, String namespace, String group, String accessToken, long beatIntervalMs) {

    public NacosSettings {
        serverAddr = (serverAddr == null || serverAddr.isBlank()) ? "127.0.0.1:8848" : serverAddr.trim();
        namespace = (namespace == null || namespace.isBlank()) ? "public" : namespace.trim();
        group = (group == null || group.isBlank()) ? "DEFAULT" : group.trim();
        beatIntervalMs = beatIntervalMs <= 0 ? 5000 : beatIntervalMs;
    }

    public static NacosSettings defaults() {
        return new NacosSettings("127.0.0.1:8848", "public", "DEFAULT", null, 5000);
    }
}

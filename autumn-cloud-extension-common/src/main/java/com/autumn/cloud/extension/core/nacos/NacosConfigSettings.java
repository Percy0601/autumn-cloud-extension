package com.autumn.cloud.extension.core.nacos;

/**
 * Nacos 配置中心连接配置（纯 Java，无 Spring 依赖）。
 *
 * @param serverAddr       Nacos 服务端地址 host:port
 * @param namespace        命名空间
 * @param defaultGroup     默认分组
 * @param accessToken      鉴权 Token
 * @param pollingTimeoutMs 长轮询超时（毫秒）
 */
public record NacosConfigSettings(
        String serverAddr, String namespace, String defaultGroup, String accessToken, long pollingTimeoutMs) {

    public NacosConfigSettings {
        serverAddr = (serverAddr == null || serverAddr.isBlank()) ? "127.0.0.1:8848" : serverAddr.trim();
        namespace = (namespace == null || namespace.isBlank()) ? "public" : namespace.trim();
        defaultGroup = (defaultGroup == null || defaultGroup.isBlank()) ? "DEFAULT_GROUP" : defaultGroup.trim();
        pollingTimeoutMs = pollingTimeoutMs <= 0 ? 30000 : pollingTimeoutMs;
    }

    public static NacosConfigSettings defaults() {
        return new NacosConfigSettings("127.0.0.1:8848", "public", "DEFAULT_GROUP", null, 30000);
    }
}

package com.autumn.cloud.extension.core.api;

import java.util.Map;

/**
 * 服务实例接口（发现侧视角）。
 *
 * <p>该接口表示从注册中心发现的服务实例信息，是服务消费方使用的核心抽象。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *     <li>纯 Java 接口，无框架依赖</li>
 *     <li>不可变对象，线程安全</li>
 *     <li>适配 GraalVM Native Image（无反射）</li>
 * </ul>
 */
public interface ServiceInstance {

    /**
     * 实例唯一标识
     */
    String getInstanceId();

    /**
     * 服务名
     */
    String getServiceId();

    /**
     * 实例主机地址
     */
    String getHost();

    /**
     * 实例端口
     */
    int getPort();

    /**
     * 是否安全连接（HTTPS）
     */
    boolean isSecure();

    /**
     * 实例 URI（如 http://10.0.0.1:8080）
     */
    String getUri();

    /**
     * 实例元数据
     */
    Map<String, String> getMetadata();

    /**
     * 获取流量（元数据中的 traffic）
     */
    default String getTraffic() {
        return getMetadata().get("traffic");
    }
}

package com.autumn.cloud.extension.core.api;

import java.util.Map;

/**
 * 服务实例注册信息接口（通用抽象）。
 *
 * <p>该接口定义了服务注册时需要的基本信息，各后端（Consul/Nacos/Multicast）
 * 可以根据自身特性实现该接口。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *     <li>纯 Java 接口，无框架依赖</li>
 *     <li>不可变对象，线程安全</li>
 *     <li>适配 GraalVM Native Image（无反射）</li>
 * </ul>
 */
public interface Registration {

    /**
     * 服务名（如 user-service、order-service）
     */
    String getServiceId();

    /**
     * 实例唯一标识（如 user-service-10.0.0.1:8080）
     */
    String getInstanceId();

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
     * 实例元数据（可携带流量、版本等信息）
     */
    Map<String, String> getMetadata();

    /**
     * 获取流量（元数据中的 traffic）
     */
    default String getTraffic() {
        return getMetadata().get("traffic");
    }
}

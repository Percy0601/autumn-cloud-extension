package com.autumn.cloud.extension.core.api;

import java.util.List;

/**
 * 服务发现客户端接口（通用抽象）。
 *
 * <p>该接口定义了服务发现的核心能力，各后端（Consul/Nacos/Multicast）
 * 需要实现该接口以提供统一的服务发现能力。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ServiceDiscoveryClient client = new ConsulServiceDiscoveryClient(consulClient);
 * List<ServiceInstance> instances = client.getInstances("user-service");
 * List<String> services = client.getServices();
 * }</pre>
 *
 * <p>设计原则：</p>
 * <ul>
 *     <li>纯 Java 接口，无框架依赖</li>
 *     <li>阻塞式调用（同步返回）</li>
 *     <li>适配 GraalVM Native Image（无反射）</li>
 * </ul>
 */
public interface ServiceDiscoveryClient {

    /**
     * 获取指定服务的全部健康实例
     *
     * @param serviceId 服务名
     * @return 实例列表，无实例时返回空列表
     */
    List<ServiceInstance> getInstances(String serviceId);

    /**
     * 获取全部已注册服务名
     *
     * @return 服务名列表
     */
    List<String> getServices();

    /**
     * 客户端描述（用于日志/调试）
     */
    String description();
}

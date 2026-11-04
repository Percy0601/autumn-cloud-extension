package com.autumn.cloud.extension.core.api;

/**
 * 服务注册客户端接口（通用抽象）。
 *
 * <p>该接口定义了服务注册的核心能力，各后端（Consul/Nacos/Multicast）
 * 需要实现该接口以提供统一的服务注册能力。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ServiceRegistryClient client = new ConsulServiceRegistryClient(consulClient);
 * client.register(registration);
 * // ... 服务运行 ...
 * client.deregister(registration.getInstanceId());
 * client.close();
 * }</pre>
 *
 * <p>设计原则：</p>
 * <ul>
 *     <li>纯 Java 接口，无框架依赖</li>
 *     <li>支持健康检查自动续约（由实现决定）</li>
 *     <li>适配 GraalVM Native Image（无反射）</li>
 * </ul>
 */
public interface ServiceRegistryClient extends AutoCloseable {

    /**
     * 注册服务实例
     *
     * @param registration 注册信息
     */
    void register(Registration registration);

    /**
     * 注销服务实例
     *
     * @param instanceId 实例唯一标识
     */
    void deregister(String instanceId);

    /**
     * 客户端描述（用于日志/调试）
     */
    String description();

    /**
     * 关闭客户端，释放资源
     */
    @Override
    void close();
}

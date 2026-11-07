package xyz.wewin.autumn.cloud.extension.core.api;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 配置中心客户端接口（通用抽象）。
 *
 * <p>该接口定义了配置中心的核心能力，各后端（Consul/Nacos）
 * 需要实现该接口以提供统一的配置管理能力。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ConfigCenterClient client = new NacosConfigCenterClient(NacosConfigSettings.defaults());
 * 
 * // 获取配置
 * String config = client.getConfig("application.yaml", "DEFAULT_GROUP", 5000);
 * 
 * // 监听配置变更
 * client.addListener("application.yaml", "DEFAULT_GROUP", newConfig -> {
 *     System.out.println("Config changed: " + newConfig);
 * });
 * 
 * // 发布配置
 * client.publishConfig("application.yaml", "DEFAULT_GROUP", newYamlContent);
 * 
 * client.close();
 * }</pre>
 *
 * <p>设计原则：</p>
 * <ul>
 *     <li>纯 Java 接口，无框架依赖</li>
 *     <li>支持配置变更监听（长轮询/阻塞查询）</li>
 *     <li>适配 GraalVM Native Image（无反射）</li>
 * </ul>
 */
public interface ConfigCenterClient extends AutoCloseable {

    /**
     * 获取配置内容
     *
     * @param dataId  配置 ID（如 application.yaml）
     * @param group   分组（如 DEFAULT_GROUP）
     * @param timeout 超时时间（毫秒）
     * @return 配置内容，不存在时返回 null
     */
    String getConfig(String dataId, String group, long timeout);

    /**
     * 获取配置内容（使用默认分组）
     *
     * @param dataId  配置 ID
     * @param timeout 超时时间（毫秒）
     * @return 配置内容
     */
    default String getConfig(String dataId, long timeout) {
        return getConfig(dataId, getDefaultGroup(), timeout);
    }

    /**
     * 发布配置
     *
     * @param dataId  配置 ID
     * @param group   分组
     * @param content 配置内容
     * @return 是否发布成功
     */
    boolean publishConfig(String dataId, String group, String content);

    /**
     * 发布配置（使用默认分组）
     *
     * @param dataId  配置 ID
     * @param content 配置内容
     * @return 是否发布成功
     */
    default boolean publishConfig(String dataId, String content) {
        return publishConfig(dataId, getDefaultGroup(), content);
    }

    /**
     * 删除配置
     *
     * @param dataId 配置 ID
     * @param group  分组
     * @return 是否删除成功
     */
    boolean removeConfig(String dataId, String group);

    /**
     * 添加配置监听器
     *
     * @param dataId   配置 ID
     * @param group    分组
     * @param listener 配置变更回调（参数为新配置内容）
     */
    void addListener(String dataId, String group, Consumer<String> listener);

    /**
     * 添加配置监听器（使用默认分组）
     *
     * @param dataId   配置 ID
     * @param listener 配置变更回调
     */
    default void addListener(String dataId, Consumer<String> listener) {
        addListener(dataId, getDefaultGroup(), listener);
    }

    /**
     * 移除配置监听器
     *
     * @param dataId   配置 ID
     * @param group    分组
     * @param listener 要移除的监听器
     */
    void removeListener(String dataId, String group, Consumer<String> listener);

    /**
     * 获取默认分组名
     */
    String getDefaultGroup();

    /**
     * 获取默认命名空间
     */
    String getDefaultNamespace();

    /**
     * 客户端描述（用于日志/调试）
     */
    String description();

    /**
     * 关闭客户端，释放资源（停止所有监听器）
     */
    @Override
    void close();
}

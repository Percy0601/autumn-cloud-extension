package com.autumn.cloud.extension.core.api;

import java.util.function.Consumer;

/**
 * 配置监听器接口。
 *
 * <p>用于接收配置变更通知，支持函数式接口。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ConfigListener listener = event -> {
 *     System.out.println("Config changed: " + event.dataId());
 *     System.out.println("New content: " + event.newContent());
 * };
 * client.addListener("application.yaml", "DEFAULT_GROUP", listener);
 * }</pre>
 */
@FunctionalInterface
public interface ConfigListener extends Consumer<ConfigChangeEvent> {

    /**
     * 接收配置变更事件
     *
     * @param event 配置变更事件
     */
    @Override
    void accept(ConfigChangeEvent event);
}

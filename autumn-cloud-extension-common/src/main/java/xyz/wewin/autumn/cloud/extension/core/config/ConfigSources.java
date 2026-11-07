package xyz.wewin.autumn.cloud.extension.core.config;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 配置中心内容解析工具（纯 Java，无 Spring 依赖）。
 *
 * <p>远程配置约定使用 {@code .properties} 格式，例如：</p>
 * <pre>
 * demo.config.value=from-remote
 * demo.config.timeout=5000
 * </pre>
 */
public final class ConfigSources {

    /** 注入 Spring Environment 的 PropertySource 名称，配置刷新时据此定位并原地更新。 */
    public static final String PROPERTY_SOURCE_NAME = "autumnConfigCenter";

    private ConfigSources() {
    }

    /**
     * 把远程配置文本解析为属性 Map；空内容返回空 Map。
     *
     * @throws IllegalArgumentException 内容不是合法的 properties 格式
     */
    public static Map<String, Object> parse(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            return result;
        }
        Properties properties = new Properties();
        try (Reader reader = new StringReader(content)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("远程配置不是合法的 .properties 格式: " + e.getMessage(), e);
        }
        for (String key : properties.stringPropertyNames()) {
            result.put(key, properties.getProperty(key));
        }
        return result;
    }
}

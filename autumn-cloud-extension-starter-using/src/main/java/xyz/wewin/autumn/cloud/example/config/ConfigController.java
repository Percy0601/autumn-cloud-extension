package xyz.wewin.autumn.cloud.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 配置中心示例 Controller。
 *
 * <p>使用 {@code @RefreshScope} 注解，当配置中心配置变更时，
 * Spring Cloud 会自动刷新该 Bean，重新注入配置值。</p>
 *
 * <p>测试步骤：</p>
 * <ol>
 *     <li>启动 ConfigApplication（profiles=config）</li>
 *     <li>访问 http://localhost:8103/config 查看当前配置</li>
 *     <li>在 Nacos/Consul 控制台修改配置</li>
 *     <li>再次访问 /config，观察配置值自动更新</li>
 * </ol>
 */
@RestController
@RefreshScope
public class ConfigController {

    @Value("${demo.config.value:default-value}")
    private String configValue;

    @Value("${demo.config.timeout:3000}")
    private int timeout;

    @Value("${demo.config.feature.enabled:false}")
    private boolean featureEnabled;

    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return Map.of(
                "configValue", configValue,
                "timeout", timeout,
                "featureEnabled", featureEnabled,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    @GetMapping("/config/reload")
    public Map<String, String> reload() {
        // 配置刷新由 ConfigRefreshListener 自动触发
        // 此接口仅用于演示，返回当前配置值
        return Map.of(
                "message", "Config values are automatically refreshed via @RefreshScope",
                "configValue", configValue,
                "timeout", String.valueOf(timeout),
                "featureEnabled", String.valueOf(featureEnabled)
        );
    }
}

package xyz.wewin.autumn.cloud.example.config;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 配置中心示例：演示配置动态刷新功能。
 *
 * <p>运行方式：</p>
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.main-class=xyz.wewin.autumn.cloud.example.config.ConfigApplication \
 *     -Dspring-boot.run.profiles=config
 * </pre>
 *
 * <p>功能说明：</p>
 * <ul>
 *     <li>启动时从配置中心加载配置</li>
 *     <li>监听配置变更并自动刷新</li>
 *     <li>通过 /config 接口查看当前配置值</li>
 * </ul>
 */
@SpringBootApplication
public class ConfigApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ConfigApplication.class).run(args);
    }
}

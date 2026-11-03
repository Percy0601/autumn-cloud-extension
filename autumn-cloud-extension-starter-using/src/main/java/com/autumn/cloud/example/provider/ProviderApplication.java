package com.autumn.cloud.example.provider;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 服务提供方示例：启动后自动注册到注册中心（携带流量标签）。
 *
 * <p>运行方式见 README / docs/usage.md，例如：</p>
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.main-class=com.autumn.cloud.example.provider.ProviderApplication \
 *     -Dspring-boot.run.profiles=provider
 * </pre>
 */
@SpringBootApplication
public class ProviderApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ProviderApplication.class).run(args);
    }
}

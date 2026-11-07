package xyz.wewin.autumn.cloud.example.gateway;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 网关示例：lb:// 路由 + 流量标签负载均衡。
 *
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.profiles=gateway
 * </pre>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(GatewayApplication.class).run(args);
    }
}

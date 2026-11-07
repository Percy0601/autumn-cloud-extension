package xyz.wewin.autumn.cloud.example.provider;

import xyz.wewin.autumn.cloud.extension.core.util.Networks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 返回实例信息，便于直观验证流量标签路由命中的实例。
 */
@RestController
public class ProviderController {

    @Value("${spring.application.name}")
    private String service;

    @Value("${server.port}")
    private int port;

    @Value("${autumn.discovery.traffic:default}")
    private String traffic;

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "service", service,
                "host", Networks.localHostAddress(),
                "port", port,
                "traffic", traffic);
    }
}

package xyz.wewin.autumn.cloud.example.propagation;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientHttpServiceGroupConfigurer;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.HttpServiceGroup;
import org.springframework.web.service.registry.ImportHttpServices;

import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * reactive 侧示例 + 测试：WebFlux 服务用 {@code @HttpExchange} 声明式客户端调下游，
 * 验证入站 {@code X-Traffic} 能级联透传、{@code traceparent} 不会被复制。
 *
 * <p>想在自己工程里验证，照这个类抄三样东西：</p>
 * <ol>
 *   <li>{@code @ImportHttpServices} 声明客户端接口；</li>
 *   <li>配置 {@code autumn.propagation.enabled=true}（include 默认就是 {@code X-Traffic}）；</li>
 *   <li>起一个假下游（这里用 JDK 自带 HttpServer），断言它收到的 header。</li>
 * </ol>
 */
@SpringBootTest(
        classes = ReactivePropagationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "autumn.propagation.enabled=true",
                "autumn.propagation.include=X-Traffic",
                // 示例模块引了全部 Starter，测试里关掉服务发现注册与负载均衡，避免把 127.0.0.1 当服务名
                "autumn.discovery.register-enabled=false",
                "spring.cloud.loadbalancer.enabled=false"
        })
class ReactivePropagationTest {

    private static final AtomicReference<String> RECEIVED_TRAFFIC = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_TRACEPARENT = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM = startDownstream();

    @Value("${local.server.port}")
    int port;

    @Test
    void chainCallKeepsTrafficLabel() throws Exception {
        RECEIVED_TRAFFIC.set(null);
        RECEIVED_TRACEPARENT.set(null);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/chain"))
                .header("X-Traffic", "gray")
                .header("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("ok", response.body());
        assertEquals("gray", RECEIVED_TRAFFIC.get(), "第二跳应带上第一跳的泳道标签");
        assertNull(RECEIVED_TRACEPARENT.get(), "traceparent 必须由中间件重新生成，不能原样复制");
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chain", exchange -> {
                RECEIVED_TRAFFIC.set(exchange.getRequestHeaders().getFirst("X-Traffic"));
                RECEIVED_TRACEPARENT.set(exchange.getRequestHeaders().getFirst("traceparent"));
                byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ImportHttpServices(types = ChainClient.class, group = "downstream",
            clientType = HttpServiceGroup.ClientType.WEB_CLIENT)
    static class TestApplication {

        @Bean
        WebClient.Builder webClientBuilder() {
            return WebClient.builder();
        }

        @Bean
        WebClientHttpServiceGroupConfigurer downstreamBaseUrl() {
            String baseUrl = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
            return groups -> groups.filterByName("downstream")
                    .forEachClient((group, builder) -> builder.baseUrl(baseUrl));
        }

        @Bean
        ChainController chainController(ChainClient client) {
            return new ChainController(client);
        }
    }

    interface ChainClient {

        @GetExchange("/api/chain")
        Mono<String> call();
    }

    @RestController
    static class ChainController {

        private final ChainClient client;

        ChainController(ChainClient client) {
            this.client = client;
        }

        @GetMapping("/chain")
        Mono<String> chain() {
            return client.call();
        }
    }
}

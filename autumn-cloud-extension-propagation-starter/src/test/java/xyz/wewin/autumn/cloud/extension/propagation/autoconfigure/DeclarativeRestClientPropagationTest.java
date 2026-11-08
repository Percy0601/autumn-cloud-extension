package xyz.wewin.autumn.cloud.extension.propagation.autoconfigure;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.ImportHttpServices;

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
 * servlet 侧端到端：@ImportHttpServices 声明的 RestClient 客户端，由 starter 自动挂上 Processor。
 *
 * <p>入站请求带 X-Traffic → 控制器调用声明式客户端 → 断言真实下游收到了该 header，
 * 同时确认 traceparent 没有被复制。</p>
 */
@SpringBootTest(
        classes = DeclarativeRestClientPropagationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "autumn.propagation.enabled=true",
                "autumn.propagation.include=X-Traffic"
        })
class DeclarativeRestClientPropagationTest {

    private static final AtomicReference<String> RECEIVED_TRAFFIC = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_TRACEPARENT = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM = startDownstream();

    @Value("${local.server.port}")
    int port;

    @Test
    void whitelistedHeaderReachesDownstream() throws Exception {
        RECEIVED_TRAFFIC.set(null);
        RECEIVED_TRACEPARENT.set(null);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/forward"))
                .header("X-Traffic", "gray")
                .header("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("gray", RECEIVED_TRAFFIC.get(), "自动装配后白名单 header 应到达下游");
        assertNull(RECEIVED_TRACEPARENT.get(), "traceparent 必须由追踪中间件生成，不能被复制");
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/downstream", exchange -> {
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
    @ImportHttpServices(types = DownstreamClient.class, group = "downstream")
    static class TestApplication {

        @Bean
        RestClientHttpServiceGroupConfigurer downstreamBaseUrl() {
            String baseUrl = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
            return groups -> groups.filterByName("downstream")
                    .forEachClient((group, builder) -> builder.baseUrl(baseUrl));
        }

        @Bean
        ForwardController forwardController(DownstreamClient client) {
            return new ForwardController(client);
        }
    }

    interface DownstreamClient {

        @GetExchange("/api/downstream")
        String call();
    }

    @RestController
    static class ForwardController {

        private final DownstreamClient client;

        ForwardController(DownstreamClient client) {
            this.client = client;
        }

        @GetMapping("/forward")
        String forward() {
            return client.call();
        }
    }
}

package xyz.wewin.autumn.cloud.extension.propagation;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 端到端验证：servlet 入站请求 → @HttpExchange 代理 → 真实出站 HTTP 请求。
 */
class EndToEndPropagationTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void headerFromInboundRequestReachesDownstreamService() throws Exception {
        AtomicReference<String> traffic = new AtomicReference<>();
        AtomicReference<String> traceParent = new AtomicReference<>();
        server = startServer(traffic, traceParent);

        HeaderPropagation propagation = HeaderPropagation.of(new HeaderPropagationProperties(
                true, PropagationMode.WHITELIST, List.of("X-Traffic"), List.of(), 32, false));

        DemoClient client = newClient(propagation);

        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader("X-Traffic", "gray");
        inbound.addHeader("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        client.call();

        assertEquals("gray", traffic.get(), "白名单内的业务 header 应透传到下游");
        assertNull(traceParent.get(), "traceparent 必须由追踪中间件生成，不能被复制");
    }

    @Test
    void downstreamReceivesNothingWhenSwitchIsOff() throws Exception {
        AtomicReference<String> traffic = new AtomicReference<>();
        AtomicReference<String> traceParent = new AtomicReference<>();
        server = startServer(traffic, traceParent);

        HeaderPropagation propagation = HeaderPropagation.of(new HeaderPropagationProperties(
                false, PropagationMode.WHITELIST, List.of("X-Traffic"), List.of(), 32, false));

        DemoClient client = newClient(propagation);

        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader("X-Traffic", "gray");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        client.call();

        assertNull(traffic.get(), "默认关闭时不透传");
    }

    private DemoClient newClient(HeaderPropagation propagation) {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .httpRequestValuesProcessor(propagation.processor())
                .build();
        return factory.createClient(DemoClient.class);
    }

    private HttpServer startServer(AtomicReference<String> traffic, AtomicReference<String> traceParent) throws Exception {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/api/demo", exchange -> {
            traffic.set(exchange.getRequestHeaders().getFirst("X-Traffic"));
            traceParent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        httpServer.start();
        return httpServer;
    }

    interface DemoClient {

        @GetExchange("/api/demo")
        void call();
    }
}

package xyz.wewin.autumn.cloud.extension.propagation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveHeaderPropagationFilterTest {

    private static final URI TARGET = URI.create("http://demo-service/api/demo");

    private final HeaderPropagation propagation = HeaderPropagation.of(new HeaderPropagationProperties(
            true, PropagationMode.WHITELIST, List.of("X-Traffic"), List.of(), 32, false));

    private final ReactiveHeaderPropagationFilter filter = new ReactiveHeaderPropagationFilter(propagation);

    @Test
    void copiesHeaderFromExchangeInReactorContext() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        filter.filter(request(), next(captured))
                .contextWrite(context -> context.put(ServerWebExchange.class, exchange("gray")))
                .block();

        assertEquals(List.of("gray"), captured.get().headers().get("X-Traffic"));
    }

    @Test
    void readsExchangeWrittenByTheOfficialContextFilter() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        // ServerWebExchangeContextFilter 用一个「带前缀的字符串」做 context key，属于框架约定，必须兼容
        filter.filter(request(), next(captured))
                .contextWrite(context -> context.put(
                        ServerWebExchangeContextFilter.EXCHANGE_CONTEXT_ATTRIBUTE, exchange("gray")))
                .block();

        assertEquals(List.of("gray"), captured.get().headers().get("X-Traffic"));
    }

    @Test
    void passesRequestThroughWhenContextIsMissing() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        filter.filter(request(), next(captured)).block();

        assertFalse(captured.get().headers().containsHeader("X-Traffic"));
    }

    @Test
    void doesNotOverwriteHeaderAlreadyOnOutboundRequest() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ClientRequest request = ClientRequest.create(HttpMethod.GET, TARGET)
                .header("X-Traffic", "explicit")
                .build();

        filter.filter(request, next(captured))
                .contextWrite(context -> context.put(ServerWebExchange.class, exchange("gray")))
                .block();

        assertEquals(List.of("explicit"), captured.get().headers().get("X-Traffic"));
    }

    @Test
    void skipsWhenDisabled() {
        HeaderPropagation disabled = HeaderPropagation.of(new HeaderPropagationProperties(
                false, PropagationMode.WHITELIST, List.of("X-Traffic"), List.of(), 32, false));
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        new ReactiveHeaderPropagationFilter(disabled)
                .filter(request(), next(captured))
                .contextWrite(context -> context.put(ServerWebExchange.class, exchange("gray")))
                .block();

        assertFalse(captured.get().headers().containsHeader("X-Traffic"));
    }

    @Test
    void neverRelaysTraceParent() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        filter.filter(request(), next(captured))
                .contextWrite(context -> context.put(ServerWebExchange.class, exchange("gray")))
                .block();

        List<String> traceParent = captured.get().headers().get("traceparent");
        assertTrue(traceParent == null || traceParent.isEmpty());
    }

    private static ClientRequest request() {
        return ClientRequest.create(HttpMethod.GET, TARGET).build();
    }

    private static ServerWebExchange exchange(String traffic) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/demo")
                .header("X-Traffic", traffic)
                .header("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
                .build());
    }

    private static ExchangeFunction next(AtomicReference<ClientRequest> captured) {
        return request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        };
    }
}

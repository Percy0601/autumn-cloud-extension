package xyz.wewin.autumn.cloud.extension.propagation;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * reactive（WebFlux / Gateway）场景的透传实现，挂到 {@code WebClient.Builder#filter}。
 *
 * <p>WebFlux 下当前请求不在 ThreadLocal 里，只能从 Reactor Context 取
 * {@link ServerWebExchange}（需要链路里有 {@code ServerWebExchangeContextFilter}
 * 之类的过滤器把 exchange 写入 context）。取不到时静默跳过，不影响请求本身。</p>
 *
 * <p>由于是在 filter 里改写 {@link ClientRequest}，它同样遵守"已有 header 不覆盖"，
 * 链路追踪中间件后续写入的 {@code traceparent} 不会被这里干扰。</p>
 */
public final class ReactiveHeaderPropagationFilter implements ExchangeFilterFunction {

    private final HeaderPropagation propagation;
    private final Function<ContextView, ServerWebExchange> exchangeLookup;

    public ReactiveHeaderPropagationFilter(HeaderPropagation propagation) {
        this(propagation, ReactiveHeaderPropagationFilter::lookupExchange);
    }

    /**
     * @param exchangeLookup 自定义从 Reactor Context 取当前请求的方式（context key 与框架版本相关时可替换）
     */
    public ReactiveHeaderPropagationFilter(HeaderPropagation propagation,
                                          Function<ContextView, ServerWebExchange> exchangeLookup) {
        this.propagation = Objects.requireNonNull(propagation, "propagation must not be null");
        this.exchangeLookup = Objects.requireNonNull(exchangeLookup, "exchangeLookup must not be null");
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        HeaderPropagationPolicy policy = propagation.policy();
        if (!policy.isEnabled()) {
            return next.exchange(request);
        }
        return Mono.deferContextual(context -> propagate(policy, request, next, context));
    }

    private Mono<ClientResponse> propagate(HeaderPropagationPolicy policy, ClientRequest request,
                                          ExchangeFunction next, ContextView context) {
        ServerWebExchange exchange = exchangeLookup.apply(context);
        if (exchange == null) {
            return next.exchange(request);
        }
        HttpHeaders inbound = exchange.getRequest().getHeaders();
        List<String> selected = policy.select(inbound.headerNames(), request.headers().headerNames());
        if (selected.isEmpty()) {
            return next.exchange(request);
        }
        ClientRequest mutated = ClientRequest.from(request)
                .headers(target -> {
                    for (String name : selected) {
                        List<String> values = inbound.get(name);
                        if (values != null && !values.isEmpty()) {
                            target.put(name, new ArrayList<>(values));
                        }
                    }
                })
                .build();
        return next.exchange(mutated);
    }

    private static ServerWebExchange lookupExchange(ContextView context) {
        // 官方约定：ServerWebExchangeContextFilter 以「类名 String」为 key 写入 context，
        // 不在 context 里时 getExchange 返回 Optional.empty（不会抛 NoSuchElementException）
        ServerWebExchange exchange = ServerWebExchangeContextFilter.getExchange(context).orElse(null);
        if (exchange != null) {
            return exchange;
        }
        // 兜底：调用方自己 contextWrite(ServerWebExchange.class, exchange) 的场景
        Object value = context.getOrEmpty(ServerWebExchange.class).orElse(null);
        return value instanceof ServerWebExchange serverExchange ? serverExchange : null;
    }
}

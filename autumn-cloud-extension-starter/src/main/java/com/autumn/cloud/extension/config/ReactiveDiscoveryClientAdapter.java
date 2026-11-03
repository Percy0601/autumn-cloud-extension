package com.autumn.cloud.extension.config;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 阻塞 DiscoveryClient 的响应式适配（对应 spring-cloud-consul 的 ConsulReactiveDiscoveryClient 语义）。
 *
 * <p>网关（WebFlux）基于 ReactiveDiscoveryClient 动态生成路由，本适配将阻塞调用切换到 boundedElastic。</p>
 */
public class ReactiveDiscoveryClientAdapter implements ReactiveDiscoveryClient {

    private final DiscoveryClient delegate;

    public ReactiveDiscoveryClientAdapter(DiscoveryClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public String description() {
        return "Reactive adapter for " + delegate.description();
    }

    @Override
    public Flux<ServiceInstance> getInstances(String serviceId) {
        return Mono.fromCallable(() -> delegate.getInstances(serviceId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<String> getServices() {
        return Mono.fromCallable(delegate::getServices)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .distinct();
    }

    @Override
    public int getOrder() {
        return delegate.getOrder();
    }

    public List<ServiceInstance> getInstancesBlocking(String serviceId) {
        return delegate.getInstances(serviceId);
    }
}

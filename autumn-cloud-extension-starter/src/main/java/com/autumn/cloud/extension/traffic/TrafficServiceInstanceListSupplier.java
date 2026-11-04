package com.autumn.cloud.extension.traffic;

import com.autumn.cloud.extension.core.traffic.Traffic;
import com.autumn.cloud.extension.core.traffic.TrafficSelector;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 流量感知的 LoadBalancer 实例列表供应商。
 *
 * <p>调用链（对齐 autumn-gateway 的扩展模式）：</p>
 * <pre>
 * ReactorLoadBalancer（RoundRobin）
 *   → TrafficServiceInstanceListSupplier.get(request)   ← 按 header 流量过滤
 *       → DiscoveryClientServiceInstanceListSupplier
 *           → Consul/Nacos/Multicast DiscoveryClient
 * </pre>
 *
 * <p>流量匹配规则见 {@link TrafficSelector}；header 未携带时走 main 主干流量。</p>
 */
public class TrafficServiceInstanceListSupplier extends DiscoveryClientServiceInstanceListSupplier {

    private final TrafficProperties properties;

    public TrafficServiceInstanceListSupplier(DiscoveryClient delegate, Environment environment,
                                              TrafficProperties properties) {
        super(delegate, environment);
        this.properties = properties;
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return super.get().map(instances -> select(instances, Traffic.DEFAULT));
    }

    @Override
    public Flux<List<ServiceInstance>> get(Request request) {
        return super.get().map(instances -> select(instances, resolveTraffic(request)));
    }

    private List<ServiceInstance> select(List<ServiceInstance> instances, String traffic) {
        return TrafficSelector.select(instances, traffic,
                instance -> instance.getMetadata().get(Traffic.META_KEY),
                properties.fallbackToMain());
    }

    /** 从 LB 请求上下文（网关转发链路）解析流量 header。 */
    private String resolveTraffic(Request<?> request) {
        if (request.getContext() instanceof RequestDataContext context) {
            String value = context.getClientRequest().getHeaders().getFirst(properties.header());
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return Traffic.DEFAULT;
    }
}

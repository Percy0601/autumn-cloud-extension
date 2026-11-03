package com.autumn.cloud.extension.trafficlabel;

import com.autumn.cloud.extension.core.trafficlabel.TrafficLabel;
import com.autumn.cloud.extension.core.trafficlabel.TrafficLabelSelector;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 流量标签感知的 LoadBalancer 实例列表供应商。
 *
 * <p>调用链（对齐 autumn-gateway 的扩展模式）：</p>
 * <pre>
 * ReactorLoadBalancer（RoundRobin）
 *   → TrafficLabelServiceInstanceListSupplier.get(request)   ← 按 header 流量标签过滤
 *       → DiscoveryClientServiceInstanceListSupplier
 *           → Consul/Nacos/Multicast DiscoveryClient
 * </pre>
 *
 * <p>流量标签匹配规则见 {@link TrafficLabelSelector}；header 未携带时走 default 流量标签。</p>
 */
public class TrafficLabelServiceInstanceListSupplier extends DiscoveryClientServiceInstanceListSupplier {

    private final TrafficLabelProperties properties;

    public TrafficLabelServiceInstanceListSupplier(DiscoveryClient delegate, Environment environment,
                                           TrafficLabelProperties properties) {
        super(delegate, environment);
        this.properties = properties;
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return super.get().map(instances -> select(instances, TrafficLabel.DEFAULT_LABEL));
    }

    @Override
    public Flux<List<ServiceInstance>> get(Request request) {
        return super.get().map(instances -> select(instances, resolveLane(request)));
    }

    private List<ServiceInstance> select(List<ServiceInstance> instances, String trafficLabel) {
        return TrafficLabelSelector.select(instances, trafficLabel,
                instance -> instance.getMetadata().get(TrafficLabel.META_KEY),
                properties.fallbackToDefault());
    }

    /** 从 LB 请求上下文（网关转发链路）解析流量标签 header。 */
    private String resolveLane(org.springframework.cloud.client.loadbalancer.Request<?> request) {
        if (request.getContext() instanceof RequestDataContext context) {
            String value = context.getClientRequest().getHeaders().getFirst(properties.headerName());
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return TrafficLabel.DEFAULT_LABEL;
    }
}

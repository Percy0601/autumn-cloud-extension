package xyz.wewin.autumn.cloud.extension.discovery.consul;

import xyz.wewin.autumn.cloud.extension.core.consul.ConsulClient;
import xyz.wewin.autumn.cloud.extension.core.model.InstanceSnapshot;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;

/**
 * Consul 服务发现客户端（Spring Cloud DiscoveryClient 适配）。
 */
public class ConsulDiscoveryClient implements DiscoveryClient {

    private final ConsulClient client;

    public ConsulDiscoveryClient(ConsulClient client) {
        this.client = client;
    }

    @Override
    public String description() {
        return "Autumn Consul Discovery (HTTP API)";
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        return client.listInstances(serviceId).stream()
                .<ServiceInstance>map(snapshot -> toServiceInstance(serviceId, snapshot))
                .toList();
    }

    @Override
    public List<String> getServices() {
        return client.listServiceNames();
    }

    private static ServiceInstance toServiceInstance(String serviceId, InstanceSnapshot snapshot) {
        return new DefaultServiceInstance(snapshot.instanceId(), serviceId,
                snapshot.host(), snapshot.port(), false, snapshot.metadata());
    }
}

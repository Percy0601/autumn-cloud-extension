package com.autumn.cloud.extension.nacos;

import com.autumn.cloud.extension.core.model.InstanceSnapshot;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;

/**
 * Nacos 服务发现客户端（阻塞版）。
 */
public class NacosDiscoveryClient implements DiscoveryClient {

    private final NacosClient client;

    public NacosDiscoveryClient(NacosClient client) {
        this.client = client;
    }

    @Override
    public String description() {
        return "Autumn Nacos Discovery (OpenAPI)";
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        return client.listInstances(serviceId).stream()
                .<ServiceInstance>map(snapshot -> new DefaultServiceInstance(snapshot.instanceId(), serviceId,
                        snapshot.host(), snapshot.port(), false, snapshot.metadata()))
                .toList();
    }

    @Override
    public List<String> getServices() {
        return client.listServiceNames();
    }
}

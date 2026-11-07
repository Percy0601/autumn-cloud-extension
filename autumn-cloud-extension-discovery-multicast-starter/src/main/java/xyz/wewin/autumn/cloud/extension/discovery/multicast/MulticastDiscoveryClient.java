package xyz.wewin.autumn.cloud.extension.discovery.multicast;

import xyz.wewin.autumn.cloud.extension.core.multicast.MulticastDiscovery;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;

/**
 * 组播服务发现客户端（Spring Cloud DiscoveryClient 适配，读本地注册表，无网络调用）。
 */
public class MulticastDiscoveryClient implements DiscoveryClient {

    private final MulticastDiscovery discovery;

    public MulticastDiscoveryClient(MulticastDiscovery discovery) {
        this.discovery = discovery;
    }

    @Override
    public String description() {
        return "Autumn Multicast Discovery";
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        return discovery.getInstances(serviceId).stream()
                .<ServiceInstance>map(snapshot -> new DefaultServiceInstance(snapshot.instanceId(), serviceId,
                        snapshot.host(), snapshot.port(), false, snapshot.metadata()))
                .toList();
    }

    @Override
    public List<String> getServices() {
        return discovery.listServices();
    }
}

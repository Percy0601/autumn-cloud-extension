package com.autumn.cloud.example.provider;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 服务发现示例 Controller。
 *
 * <p>演示如何使用 {@link DiscoveryClient} 动态发现服务实例。</p>
 *
 * <p>接口说明：</p>
 * <ul>
 *     <li>GET /discovery/services - 获取所有已注册服务名</li>
 *     <li>GET /discovery/instances/{serviceId} - 获取指定服务的实例列表</li>
 * </ul>
 */
@RestController
@RequestMapping("/discovery")
public class DiscoveryController {

    private final DiscoveryClient discoveryClient;

    public DiscoveryController(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    /**
     * 获取所有已注册服务名
     */
    @GetMapping("/services")
    public Map<String, Object> getServices() {
        List<String> services = discoveryClient.getServices();
        return Map.of(
                "services", services,
                "count", services.size(),
                "description", discoveryClient.description()
        );
    }

    /**
     * 获取指定服务的实例列表
     */
    @GetMapping("/instances/{serviceId}")
    public Map<String, Object> getInstances(@PathVariable String serviceId) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
        List<Map<String, Object>> instanceList = instances.stream()
                .map(this::toMap)
                .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("serviceId", serviceId);
        result.put("instances", instanceList);
        result.put("count", instances.size());
        return result;
    }

    private Map<String, Object> toMap(ServiceInstance instance) {
        Map<String, Object> map = new HashMap<>();
        map.put("instanceId", instance.getInstanceId());
        map.put("serviceId", instance.getServiceId());
        map.put("host", instance.getHost());
        map.put("port", instance.getPort());
        map.put("secure", instance.isSecure());
        map.put("uri", instance.getUri().toString());
        map.put("metadata", instance.getMetadata());
        // 从 metadata 中提取流量标签
        map.put("traffic", instance.getMetadata().getOrDefault("traffic", "default"));
        return map;
    }
}

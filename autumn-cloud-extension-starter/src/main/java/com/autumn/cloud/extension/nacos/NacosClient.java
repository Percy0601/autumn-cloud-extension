package com.autumn.cloud.extension.nacos;

import com.autumn.cloud.extension.core.http.HttpCallException;
import com.autumn.cloud.extension.core.http.JsonHttp;
import com.autumn.cloud.extension.core.http.QueryStrings;
import com.autumn.cloud.extension.core.model.InstanceRegistration;
import com.autumn.cloud.extension.core.model.InstanceSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nacos Naming OpenAPI v1 客户端（不依赖 Nacos SDK，仅用 apache-httpclient5）。
 *
 * <ul>
 *     <li>注册：POST /nacos/v1/ns/instance（ephemeral=true，客户端心跳模式）</li>
 *     <li>心跳：PUT /nacos/v1/ns/instance/beat</li>
 *     <li>反注册：DELETE /nacos/v1/ns/instance</li>
 *     <li>健康实例：GET /nacos/v1/ns/instance/list?healthyOnly=true</li>
 *     <li>服务列表：GET /nacos/v1/ns/service/list</li>
 * </ul>
 */
public class NacosClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NacosProperties properties;
    private final JsonHttp http;

    public NacosClient(NacosProperties properties) {
        this(properties, new JsonHttp());
    }

    NacosClient(NacosProperties properties, JsonHttp http) {
        this.properties = properties;
        this.http = http;
    }

    public void register(InstanceRegistration registration) {
        Map<String, String> params = commonParams(registration.service());
        params.put("ip", registration.host());
        params.put("port", String.valueOf(registration.port()));
        params.put("ephemeral", "true");
        try {
            params.put("metadata", MAPPER.writeValueAsString(registration.metadata()));
            http.post(namingUrl("/instance") + "?" + QueryStrings.encode(params), null);
        } catch (IOException e) {
            throw new HttpCallException("nacos register failed: " + registration.instanceId(), e);
        }
    }

    public void deregister(InstanceRegistration registration) {
        Map<String, String> params = commonParams(registration.service());
        params.put("ip", registration.host());
        params.put("port", String.valueOf(registration.port()));
        params.put("ephemeral", "true");
        try {
            http.delete(namingUrl("/instance") + "?" + QueryStrings.encode(params), null);
        } catch (IOException e) {
            throw new HttpCallException("nacos deregister failed: " + registration.instanceId(), e);
        }
    }

    public void heartbeat(InstanceRegistration registration) {
        ObjectNode beat = MAPPER.createObjectNode();
        beat.put("serviceName", registration.service());
        beat.put("ip", registration.host());
        beat.put("port", registration.port());
        beat.put("cluster", "DEFAULT");
        Map<String, String> params = commonParams(registration.service());
        try {
            params.put("beat", MAPPER.writeValueAsString(beat));
            http.put(namingUrl("/instance/beat") + "?" + QueryStrings.encode(params), null, null);
        } catch (IOException e) {
            throw new HttpCallException("nacos heartbeat failed: " + registration.instanceId(), e);
        }
    }

    /** 健康实例列表（流量标签在 metadata["traffic"]）。 */
    public List<InstanceSnapshot> listInstances(String service) {
        Map<String, String> params = commonParams(service);
        params.put("healthyOnly", "true");
        try {
            String body = http.get(namingUrl("/instance/list") + "?" + QueryStrings.encode(params), null);
            List<InstanceSnapshot> result = new ArrayList<>();
            for (JsonNode host : MAPPER.readTree(body).path("hosts")) {
                Map<String, String> metadata = new LinkedHashMap<>();
                host.path("metadata").fields().forEachRemaining(f -> metadata.put(f.getKey(), f.getValue().asText()));
                String instanceId = host.path("instanceId").asText("");
                if (instanceId.isBlank()) {
                    instanceId = host.path("ip").asText() + ":" + host.path("port").asInt();
                }
                result.add(new InstanceSnapshot(service, instanceId,
                        host.path("ip").asText(), host.path("port").asInt(), metadata));
            }
            return result;
        } catch (IOException e) {
            throw new HttpCallException("nacos list instances failed: " + service, e);
        }
    }

    public List<String> listServiceNames() {
        Map<String, String> params = commonParams(null);
        params.put("pageNo", "1");
        params.put("pageSize", "1000");
        try {
            String body = http.get(namingUrl("/service/list") + "?" + QueryStrings.encode(params), null);
            List<String> names = new ArrayList<>();
            for (JsonNode dom : MAPPER.readTree(body).path("doms")) {
                names.add(dom.asText());
            }
            return names;
        } catch (IOException e) {
            throw new HttpCallException("nacos list services failed", e);
        }
    }

    private Map<String, String> commonParams(String service) {
        Map<String, String> params = new LinkedHashMap<>();
        if (service != null) {
            params.put("serviceName", service);
        }
        params.put("groupName", properties.group());
        params.put("namespaceId", properties.namespace());
        if (properties.accessToken() != null && !properties.accessToken().isBlank()) {
            params.put("accessToken", properties.accessToken().trim());
        }
        return params;
    }

    private String namingUrl(String path) {
        return "http://" + properties.serverAddr() + "/nacos/v1/ns" + path;
    }

    @Override
    public void close() {
        http.close();
    }
}

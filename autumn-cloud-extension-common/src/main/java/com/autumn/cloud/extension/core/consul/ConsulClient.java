package com.autumn.cloud.extension.core.consul;

import com.autumn.cloud.extension.core.http.HttpCallException;
import com.autumn.cloud.extension.core.http.JsonHttp;
import com.autumn.cloud.extension.core.model.InstanceRegistration;
import com.autumn.cloud.extension.core.model.InstanceSnapshot;
import com.autumn.cloud.extension.core.traffic.Traffic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consul Agent/Catalog HTTP API 客户端（纯 HTTP 协议实现，零 SDK 依赖）。
 *
 * <p>对应 API：</p>
 * <ul>
 *     <li>注册：PUT /v1/agent/service/register</li>
 *     <li>反注册：PUT /v1/agent/service/deregister/{id}</li>
 *     <li>TTL 续约：PUT /v1/agent/check/pass/service:{id}</li>
 *     <li>健康实例：GET /v1/health/service/{name}?passing=true</li>
 *     <li>服务列表：GET /v1/catalog/services</li>
 * </ul>
 */
public class ConsulClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConsulSettings settings;
    private final JsonHttp http;

    public ConsulClient(ConsulSettings settings) {
        this(settings, new JsonHttp());
    }

    public ConsulClient(ConsulSettings settings, JsonHttp http) {
        this.settings = settings;
        this.http = http;
    }

    public void register(InstanceRegistration registration) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("ID", registration.instanceId());
        body.put("Name", registration.service());
        body.put("Address", registration.host());
        body.put("Port", registration.port());
        ObjectNode meta = body.putObject("Meta");
        registration.metadata().forEach(meta::put);
        if (registration.traffic() != null) {
            body.putArray("Tags").add(Traffic.META_KEY + "=" + registration.traffic());
        }
        ObjectNode check = body.putObject("Check");
        check.put("CheckID", checkId(registration.instanceId()));
        check.put("Name", "autumn-ttl");
        check.put("TTL", settings.healthCheckTtlSeconds() + "s");
        check.put("DeregisterCriticalServiceAfter", settings.healthCheckTtlSeconds() * 3 + "s");
        try {
            http.put(baseUrl() + "/v1/agent/service/register", MAPPER.writeValueAsString(body), headers());
        } catch (IOException e) {
            throw new HttpCallException("consul register failed: " + registration.instanceId(), e);
        }
    }

    public void deregister(String instanceId) {
        try {
            http.put(baseUrl() + "/v1/agent/service/deregister/" + urlEncode(instanceId), null, headers());
        } catch (IOException e) {
            throw new HttpCallException("consul deregister failed: " + instanceId, e);
        }
    }

    public void passCheck(String instanceId) {
        try {
            http.put(baseUrl() + "/v1/agent/check/pass/" + urlEncode(checkId(instanceId)), null, headers());
        } catch (IOException e) {
            throw new HttpCallException("consul ttl pass failed: " + instanceId, e);
        }
    }

    public List<InstanceSnapshot> listInstances(String service) {
        try {
            String body = http.get(baseUrl() + "/v1/health/service/" + urlEncode(service) + "?passing=true", headers());
            List<InstanceSnapshot> result = new ArrayList<>();
            for (JsonNode item : MAPPER.readTree(body)) {
                JsonNode svc = item.path("Service");
                String host = svc.path("Address").asText("");
                if (host.isBlank()) {
                    host = item.path("Node").path("Address").asText("127.0.0.1");
                }
                Map<String, String> metadata = new LinkedHashMap<>();
                svc.path("Meta").fields().forEachRemaining(f -> metadata.put(f.getKey(), f.getValue().asText()));
                result.add(new InstanceSnapshot(svc.path("Service").asText(service),
                        svc.path("ID").asText(), host, svc.path("Port").asInt(), metadata));
            }
            return result;
        } catch (IOException e) {
            throw new HttpCallException("consul list instances failed: " + service, e);
        }
    }

    public List<String> listServiceNames() {
        try {
            String body = http.get(baseUrl() + "/v1/catalog/services", headers());
            List<String> names = new ArrayList<>();
            MAPPER.readTree(body).fieldNames().forEachRemaining(names::add);
            return names;
        } catch (IOException e) {
            throw new HttpCallException("consul list services failed", e);
        }
    }

    public ConsulSettings settings() {
        return settings;
    }

    private String baseUrl() {
        return settings.scheme() + "://" + settings.host() + ":" + settings.port();
    }

    private Map<String, String> headers() {
        if (settings.token() == null || settings.token().isBlank()) {
            return null;
        }
        return Map.of("X-Consul-Token", settings.token().trim());
    }

    private static String checkId(String instanceId) {
        return "service:" + instanceId;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        http.close();
    }
}

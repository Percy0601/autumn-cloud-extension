package xyz.wewin.autumn.cloud.extension.core.model;

import xyz.wewin.autumn.cloud.extension.core.traffic.Traffic;

import java.util.Map;

/**
 * 服务实例注册信息（注册中心视角的完整描述）。
 *
 * @param service    服务名
 * @param instanceId 实例唯一标识，如 {@code user-service-10.0.0.1:8080}
 * @param host       主机地址
 * @param port       端口
 * @param metadata   元数据，可携带流量（key = {@link Traffic#META_KEY}）等
 */
public record InstanceRegistration(String service, String instanceId, String host, int port,
                                   Map<String, String> metadata) {

    public InstanceRegistration {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** 该实例所属流量，未打标返回 null。 */
    public String traffic() {
        return metadata.get(Traffic.META_KEY);
    }

    public InstanceSnapshot snapshot() {
        return new InstanceSnapshot(service, instanceId, host, port, metadata);
    }
}

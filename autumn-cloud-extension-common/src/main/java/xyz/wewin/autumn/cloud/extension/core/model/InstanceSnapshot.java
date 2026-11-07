package xyz.wewin.autumn.cloud.extension.core.model;

import xyz.wewin.autumn.cloud.extension.core.traffic.Traffic;

import java.util.Map;

/**
 * 服务实例快照（发现侧视角）。
 */
public record InstanceSnapshot(String service, String instanceId, String host, int port,
                               Map<String, String> metadata) {

    public InstanceSnapshot {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** 该实例所属流量，未打标返回 null。 */
    public String traffic() {
        return metadata.get(Traffic.META_KEY);
    }
}

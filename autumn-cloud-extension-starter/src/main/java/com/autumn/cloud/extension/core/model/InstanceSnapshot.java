package com.autumn.cloud.extension.core.model;

import com.autumn.cloud.extension.core.trafficlabel.TrafficLabel;

import java.util.Map;

/**
 * 服务实例快照（发现侧视角）。
 */
public record InstanceSnapshot(String service, String instanceId, String host, int port,
                               Map<String, String> metadata) {

    public InstanceSnapshot {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** 该实例所属流量标签，未打标返回 null。 */
    public String trafficLabel() {
        return metadata.get(TrafficLabel.META_KEY);
    }
}

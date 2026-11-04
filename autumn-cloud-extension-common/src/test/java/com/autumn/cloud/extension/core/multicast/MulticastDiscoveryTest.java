package com.autumn.cloud.extension.core.multicast;

import com.autumn.cloud.extension.core.model.InstanceRegistration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MulticastDiscoveryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MulticastDiscovery discovery = new MulticastDiscovery(
            new MulticastConfig("239.255.42.99", 5555, 1, 100, 1000, null));

    private final InstanceRegistration registration = new InstanceRegistration(
            "demo-service", "demo-1", "10.0.0.1", 8080, Map.of("traffic", "gray"));

    private String frame(String type, InstanceRegistration registration) throws Exception {
        return MAPPER.writeValueAsString(new MulticastDiscovery.Frame(type, registration));
    }

    @Test
    void heartbeatRegistersInstance() throws Exception {
        discovery.onFrame(frame("hb", registration), System.currentTimeMillis());
        List<com.autumn.cloud.extension.core.model.InstanceSnapshot> instances = discovery.getInstances("demo-service");
        assertEquals(1, instances.size());
        assertEquals("gray", instances.get(0).traffic());
        assertTrue(discovery.listServices().contains("demo-service"));
    }

    @Test
    void byeRemovesInstance() throws Exception {
        discovery.onFrame(frame("hb", registration), System.currentTimeMillis());
        discovery.onFrame(frame("bye", registration), System.currentTimeMillis());
        assertTrue(discovery.getInstances("demo-service").isEmpty());
    }

    @Test
    void staleInstanceEvictedBySweep() throws Exception {
        long now = System.currentTimeMillis();
        discovery.onFrame(frame("hb", registration), now);
        assertEquals(1, discovery.getInstances("demo-service").size());
        // 以未来时间清扫，等价于心跳过期
        discovery.sweep(now + 1000 + 1);
        assertTrue(discovery.getInstances("demo-service").isEmpty());
    }

    @Test
    void invalidFrameIgnored() {
        discovery.onFrame("not-a-json", System.currentTimeMillis());
        discovery.onFrame("{\"type\":\"hb\"}", System.currentTimeMillis()); // 无 registration
        assertTrue(discovery.getInstances("demo-service").isEmpty());
    }
}

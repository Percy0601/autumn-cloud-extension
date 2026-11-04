package com.autumn.cloud.extension.traffic;

import com.autumn.cloud.extension.core.traffic.Traffic;
import com.autumn.cloud.extension.core.traffic.TrafficSelector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the integration between TrafficSelector (core) and the traffic-starter adapter layer.
 */
class TrafficServiceInstanceListSupplierTest {

    record MockInstance(String serviceId, String traffic) {
        String metadataTraffic() {
            return traffic;
        }
    }

    @Test
    void selectByTrafficHeader() {
        var instances = List.of(
                new MockInstance("svc", null),
                new MockInstance("svc", "main"),
                new MockInstance("svc", "gray"));

        List<MockInstance> selected = TrafficSelector.select(instances, "gray",
                MockInstance::metadataTraffic, true);
        assertEquals(1, selected.size());
        assertEquals("gray", selected.get(0).traffic());
    }

    @Test
    void untaggedInstancesAreMain() {
        var instances = List.of(
                new MockInstance("svc", null),
                new MockInstance("svc", "gray"));

        List<MockInstance> selected = TrafficSelector.select(instances, null,
                MockInstance::metadataTraffic, false);
        assertEquals(1, selected.size());
        assertEquals(Traffic.DEFAULT, TrafficSelector.normalize(selected.get(0).traffic()));
    }

    @Test
    void fallbackToMainWhenTargetMissing() {
        var instances = List.of(
                new MockInstance("svc", "main"),
                new MockInstance("svc", "gray"));

        List<MockInstance> selected = TrafficSelector.select(instances, "beta",
                MockInstance::metadataTraffic, true);
        assertEquals(1, selected.size());
        assertEquals("main", selected.get(0).traffic());
    }

    @Test
    void noFallbackReturnsEmpty() {
        var instances = List.of(
                new MockInstance("svc", "main"),
                new MockInstance("svc", "gray"));

        List<MockInstance> selected = TrafficSelector.select(instances, "beta",
                MockInstance::metadataTraffic, false);
        assertTrue(selected.isEmpty());
    }
}

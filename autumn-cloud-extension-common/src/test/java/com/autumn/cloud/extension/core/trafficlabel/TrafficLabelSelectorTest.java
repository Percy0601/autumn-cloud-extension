package com.autumn.cloud.extension.core.trafficlabel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficLabelSelectorTest {

    record Inst(String trafficLabel) {
    }

    private static final Function<Inst, String> EXTRACTOR = Inst::trafficLabel;

    private final List<Inst> candidates = List.of(
            new Inst(null),          // 未打标 → default
            new Inst("default"),
            new Inst("gray"),
            new Inst("gray"),
            new Inst("beta"));

    @Test
    void defaultRequestSelectsOnlyDefaultInstances() {
        List<Inst> selected = TrafficLabelSelector.select(candidates, null, EXTRACTOR, false);
        assertEquals(2, selected.size());
        assertTrue(selected.stream().allMatch(i -> "default".equals(TrafficLabelSelector.normalize(i.trafficLabel()))));
    }

    @Test
    void laneRequestSelectsMatchingInstances() {
        List<Inst> selected = TrafficLabelSelector.select(candidates, "gray", EXTRACTOR, false);
        assertEquals(2, selected.size());
        assertTrue(selected.stream().allMatch(i -> "gray".equals(i.trafficLabel())));
    }

    @Test
    void missingLaneFallsBackToDefault() {
        List<Inst> selected = TrafficLabelSelector.select(
                List.of(new Inst("gray"), new Inst("default")), "beta", EXTRACTOR, true);
        assertEquals(1, selected.size());
        assertEquals("default", selected.get(0).trafficLabel());
    }

    @Test
    void missingLaneWithoutFallbackReturnsEmpty() {
        List<Inst> selected = TrafficLabelSelector.select(
                List.of(new Inst("gray"), new Inst("default")), "beta", EXTRACTOR, false);
        assertTrue(selected.isEmpty());
    }

    @Test
    void fallsBackToAllWhenDefaultLaneAlsoEmpty() {
        List<Inst> selected = TrafficLabelSelector.select(
                List.of(new Inst("gray"), new Inst("beta")), "beta-x", EXTRACTOR, true);
        assertEquals(2, selected.size());
    }

    @Test
    void defaultRequestWithoutDefaultInstancesFallsBackToAll() {
        List<Inst> selected = TrafficLabelSelector.select(
                List.of(new Inst("gray")), null, EXTRACTOR, true);
        assertEquals(1, selected.size());
    }

    @Test
    void blankLaneTreatedAsDefault() {
        assertEquals(TrafficLabelSelector.normalize("  "), TrafficLabel.DEFAULT_LABEL);
        assertEquals(TrafficLabelSelector.normalize(" gray "), "gray");
    }

    @Test
    void emptyCandidatesReturnsEmpty() {
        assertTrue(TrafficLabelSelector.select(List.of(), "gray", EXTRACTOR, true).isEmpty());
        assertTrue(TrafficLabelSelector.select(null, "gray", EXTRACTOR, true).isEmpty());
    }
}

package com.autumn.cloud.extension.core.traffic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficSelectorTest {

    record Inst(String traffic) {
    }

    private static final Function<Inst, String> EXTRACTOR = Inst::traffic;

    private final List<Inst> candidates = List.of(
            new Inst(null),          // 未打标 → main
            new Inst("main"),
            new Inst("gray"),
            new Inst("gray"),
            new Inst("beta"));

    @Test
    void mainRequestSelectsOnlyMainInstances() {
        List<Inst> selected = TrafficSelector.select(candidates, null, EXTRACTOR, false);
        assertEquals(2, selected.size());
        assertTrue(selected.stream().allMatch(i -> "main".equals(TrafficSelector.normalize(i.traffic()))));
    }

    @Test
    void trafficRequestSelectsMatchingInstances() {
        List<Inst> selected = TrafficSelector.select(candidates, "gray", EXTRACTOR, false);
        assertEquals(2, selected.size());
        assertTrue(selected.stream().allMatch(i -> "gray".equals(i.traffic())));
    }

    @Test
    void missingTrafficFallsBackToMain() {
        List<Inst> selected = TrafficSelector.select(
                List.of(new Inst("gray"), new Inst("main")), "beta", EXTRACTOR, true);
        assertEquals(1, selected.size());
        assertEquals("main", selected.get(0).traffic());
    }

    @Test
    void missingTrafficWithoutFallbackReturnsEmpty() {
        List<Inst> selected = TrafficSelector.select(
                List.of(new Inst("gray"), new Inst("main")), "beta", EXTRACTOR, false);
        assertTrue(selected.isEmpty());
    }

    @Test
    void fallsBackToAllWhenMainAlsoEmpty() {
        List<Inst> selected = TrafficSelector.select(
                List.of(new Inst("gray"), new Inst("beta")), "beta-x", EXTRACTOR, true);
        assertEquals(2, selected.size());
    }

    @Test
    void mainRequestWithoutMainInstancesFallsBackToAll() {
        List<Inst> selected = TrafficSelector.select(
                List.of(new Inst("gray")), null, EXTRACTOR, true);
        assertEquals(1, selected.size());
    }

    @Test
    void blankTrafficTreatedAsMain() {
        assertEquals(TrafficSelector.normalize("  "), Traffic.DEFAULT);
        assertEquals(TrafficSelector.normalize(" gray "), "gray");
    }

    @Test
    void emptyCandidatesReturnsEmpty() {
        assertTrue(TrafficSelector.select(List.of(), "gray", EXTRACTOR, true).isEmpty());
        assertTrue(TrafficSelector.select(null, "gray", EXTRACTOR, true).isEmpty());
    }
}

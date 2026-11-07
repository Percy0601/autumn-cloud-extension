package xyz.wewin.autumn.cloud.extension.traffic;

import xyz.wewin.autumn.cloud.extension.core.traffic.Traffic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficPropertiesTest {

    @Test
    void defaultValues() {
        var props = new TrafficProperties(Traffic.DEFAULT_HEADER, true);
        assertEquals("X-Traffic", props.header());
        assertTrue(props.fallbackToMain());
    }

    @Test
    void customValues() {
        var props = new TrafficProperties("X-Lane", false);
        assertEquals("X-Lane", props.header());
        assertTrue(!props.fallbackToMain());
    }
}

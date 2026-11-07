package xyz.wewin.autumn.cloud.extension.core.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSourcesTest {

    @Test
    void parsesPropertiesContent() {
        Map<String, Object> values = ConfigSources.parse(
                "demo.config.value=from-remote\ndemo.config.timeout=5000\n# comment\n");
        assertEquals(2, values.size());
        assertEquals("from-remote", values.get("demo.config.value"));
        assertEquals("5000", values.get("demo.config.timeout"));
    }

    @Test
    void emptyContentReturnsMutableEmptyMap() {
        Map<String, Object> values = ConfigSources.parse(null);
        assertTrue(values.isEmpty());
        values.put("k", "v");
        assertEquals("v", values.get("k"));
    }

    @Test
    void blankContentReturnsEmptyMap() {
        assertTrue(ConfigSources.parse("   ").isEmpty());
    }

    @Test
    void invalidUnicodeEscapeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigSources.parse("demo.value=\\uZZZZ"));
    }
}

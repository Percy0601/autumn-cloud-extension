package com.autumn.cloud.extension.core.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendValidatorTest {

    @Test
    void misspelledDiscoveryBackendFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BackendValidator.validateDiscovery("nacoss", marker -> true));
        assertTrue(error.getMessage().contains("nacoss"));
        assertTrue(error.getMessage().contains("consul / nacos / multicast"));
    }

    @Test
    void configuredBackendWithoutStarterFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BackendValidator.validateDiscovery("nacos", marker -> marker.contains("multicast")));
        assertTrue(error.getMessage().contains("classpath 上没有对应"));
    }

    @Test
    void singleDiscoveryStarterWithoutBackendIsAllowed() {
        assertDoesNotThrow(() -> BackendValidator.validateDiscovery(null,
                marker -> marker.contains("multicast")));
        assertDoesNotThrow(() -> BackendValidator.validateDiscovery("  ",
                marker -> marker.contains("multicast")));
    }

    @Test
    void multipleDiscoveryStartersWithoutBackendFail() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BackendValidator.validateDiscovery(null, marker -> true));
        assertTrue(error.getMessage().contains("必须显式配置"));
    }

    @Test
    void validDiscoveryBackendPasses() {
        assertDoesNotThrow(() -> BackendValidator.validateDiscovery("NACOS", marker -> true));
        assertDoesNotThrow(() -> BackendValidator.validateDiscovery("consul", marker -> true));
    }

    @Test
    void configBackendMissingWarnsButDoesNotFail() {
        assertDoesNotThrow(() -> BackendValidator.validateConfig(null,
                marker -> marker.contains("nacos")));
    }

    @Test
    void misspelledConfigBackendFailsFast() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BackendValidator.validateConfig("redis", marker -> true));
        assertTrue(error.getMessage().contains("redis"));
    }

    @Test
    void noStarterOnClasspathSkipsValidation() {
        assertDoesNotThrow(() -> BackendValidator.validateDiscovery("nacoss", marker -> false));
        assertDoesNotThrow(() -> BackendValidator.validateConfig("redis", marker -> false));
    }
}

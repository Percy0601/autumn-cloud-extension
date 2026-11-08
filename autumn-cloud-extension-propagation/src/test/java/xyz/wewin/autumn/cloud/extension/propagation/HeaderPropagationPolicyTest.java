package xyz.wewin.autumn.cloud.extension.propagation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderPropagationPolicyTest {

    private static HeaderPropagationProperties properties(boolean enabled, PropagationMode mode,
                                                          List<String> include, List<String> exclude,
                                                          int maxHeaders, boolean allowTracingHeaders) {
        return new HeaderPropagationProperties(enabled, mode, include, exclude, maxHeaders, allowTracingHeaders);
    }

    @Test
    void disabledByDefaultWhenPropertiesUseDefaults() {
        // 记录一下"默认不开启"这个契约，避免以后改默认值把别人链路悄悄打开
        HeaderPropagationProperties defaults = new HeaderPropagationProperties(
                false, PropagationMode.WHITELIST, List.of("X-Traffic"), List.of(), 32, false);
        HeaderPropagationPolicy policy = HeaderPropagationPolicy.from(defaults);
        assertFalse(policy.isEnabled());
        assertFalse(policy.allows("X-Traffic"));
        assertEquals(List.of(), policy.select(List.of("X-Traffic"), Set.of()));
    }

    @Test
    void whitelistOnlyPropagatesIncludedHeaders() {
        HeaderPropagationPolicy policy = HeaderPropagationPolicy.from(
                properties(true, PropagationMode.WHITELIST, List.of("X-Traffic", "x-tenant-id"), List.of(), 32, false));

        assertTrue(policy.allows("X-Traffic"));
        assertTrue(policy.allows("X-Tenant-Id"));
        assertFalse(policy.allows("X-Internal-Sign"));
        assertEquals(List.of("X-Traffic"), policy.select(List.of("X-Traffic", "X-Internal-Sign"), Set.of()));
    }

    @Test
    void whitelistIsCaseInsensitive() {
        HeaderPropagationPolicy policy = HeaderPropagationPolicy.from(
                properties(true, PropagationMode.WHITELIST, List.of("x-traffic"), List.of(), 32, false));

        assertTrue(policy.allows("X-TRAFFIC"));
        assertTrue(policy.allows("x-traffic"));
    }

    @Test
    void blacklistPropagatesEverythingExceptExcluded() {
        HeaderPropagationPolicy policy = HeaderPropagationPolicy.from(
                properties(true, PropagationMode.BLACKLIST, List.of(), List.of("X-Internal-Sign"), 32, false));

        assertTrue(policy.allows("X-Tenant-Id"));
        assertFalse(policy.allows("X-Internal-Sign"));
    }

    @Test
    void transportHeadersAreAlwaysBlocked() {
        HeaderPropagationPolicy policy = HeaderPropagationPolicy.from(
                properties(true, PropagationMode.BLACKLIST, List.of("Content-Length", "Host"), List.of(), 32, true));

        assertFalse(policy.allows("Content-Length"));
        assertFalse(policy.allows("Host"));
        assertFalse(policy.allows("Transfer-Encoding"));
        assertFalse(policy.allows("Cookie"));
        assertFalse(policy.allows("Set-Cookie"));
    }

    @Test
    void tracingHeadersAreBlockedByDefault() {
        HeaderPropagationPolicy policy = HeaderPropagationPolicy.from(
                properties(true, PropagationMode.BLACKLIST, List.of(), List.of(), 32, false));

        assertFalse(policy.allows("traceparent"));
        assertFalse(policy.allows("tracestate"));
        assertFalse(policy.allows("b3"));
        assertFalse(policy.allows("X-B3-TraceId"));
        assertFalse(policy.allows("uber-trace-id"));
        assertFalse(policy.allows("sw8"));
    }

    @Test
    void tracingHeadersOnlyPassWhenExplicitlyAllowedAndIncluded() {
        HeaderPropagationPolicy policy = HeaderPropagationPolicy.from(
                properties(true, PropagationMode.WHITELIST, List.of("traceparent"), List.of(), 32, true));

        assertTrue(policy.allows("traceparent"));
        // 开启 allow-tracing-headers 不等于"全部追踪头都放行"，白名单仍然起作用
        assertFalse(policy.allows("uber-trace-id"));
    }

    @Test
    void maxHeadersCapsPropagationVolume() {
        HeaderPropagationPolicy policy = HeaderPropagationPolicy.from(
                properties(true, PropagationMode.BLACKLIST, List.of(), List.of(), 2, false));

        Map<String, List<String>> inbound = new LinkedHashMap<>();
        inbound.put("X-A", List.of("1"));
        inbound.put("X-B", List.of("2"));
        inbound.put("X-C", List.of("3"));

        assertEquals(List.of("X-A", "X-B"), policy.select(inbound, Set.of()));
    }

    @Test
    void existingOutboundHeadersAreNeverOverwritten() {
        HeaderPropagationPolicy policy = HeaderPropagationPolicy.from(
                properties(true, PropagationMode.WHITELIST, List.of("X-Traffic"), List.of(), 32, false));

        assertEquals(List.of(), policy.select(List.of("X-Traffic"), Set.of("x-traffic")));
    }

    @Test
    void emptyValuesAreSkipped() {
        HeaderPropagationPolicy policy = HeaderPropagationPolicy.from(
                properties(true, PropagationMode.BLACKLIST, List.of(), List.of(), 32, false));

        Map<String, List<String>> inbound = new LinkedHashMap<>();
        inbound.put("X-Empty", List.of());
        inbound.put("X-Traffic", List.of("gray"));

        assertEquals(List.of("X-Traffic"), policy.select(inbound, Set.of()));
    }
}

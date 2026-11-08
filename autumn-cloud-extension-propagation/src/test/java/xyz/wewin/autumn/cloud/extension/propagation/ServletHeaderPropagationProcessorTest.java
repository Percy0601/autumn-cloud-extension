package xyz.wewin.autumn.cloud.extension.propagation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.service.invoker.HttpRequestValues;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServletHeaderPropagationProcessorTest {

    private final HeaderPropagation propagation = HeaderPropagation.of(new HeaderPropagationProperties(
            true, PropagationMode.WHITELIST, List.of("X-Traffic"), List.of(), 32, false));

    private final ServletHeaderPropagationProcessor processor =
            new ServletHeaderPropagationProcessor(propagation);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void copiesWhitelistedHeaderFromCurrentRequest() throws Exception {
        bindRequest("X-Traffic", "gray");

        HttpRequestValues values = invokeProcessor();

        assertEquals(List.of("gray"), values.getHeaders().get("X-Traffic"));
    }

    @Test
    void ignoresHeadersOutsideWhitelist() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Traffic", "gray");
        request.addHeader("X-Internal-Sign", "secret");
        bind(request);

        HttpRequestValues values = invokeProcessor();

        assertEquals(List.of("gray"), values.getHeaders().get("X-Traffic"));
        assertFalse(values.getHeaders().containsHeader("X-Internal-Sign"));
    }

    @Test
    void doesNotOverwriteExplicitlyDeclaredHeader() throws Exception {
        bindRequest("X-Traffic", "gray");

        HttpRequestValues.Builder builder = HttpRequestValues.builder().setUriTemplate("/api/demo");
        builder.addHeader("X-Traffic", "explicit");
        invokeProcessor(builder);

        assertEquals(List.of("explicit"), builder.build().getHeaders().get("X-Traffic"));
    }

    @Test
    void skipsWhenDisabled() throws Exception {
        HeaderPropagation disabled = HeaderPropagation.of(new HeaderPropagationProperties(
                false, PropagationMode.WHITELIST, List.of("X-Traffic"), List.of(), 32, false));
        bindRequest("X-Traffic", "gray");

        HttpRequestValues.Builder builder = HttpRequestValues.builder().setUriTemplate("/api/demo");
        new ServletHeaderPropagationProcessor(disabled)
                .process(method(), new org.springframework.core.MethodParameter[0], new Object[0], builder);

        assertFalse(builder.build().getHeaders().containsHeader("X-Traffic"));
    }

    @Test
    void skipsSilentlyOutsideServletRequest() throws Exception {
        // 模拟 @Async / 线程池场景：RequestContextHolder 为空
        HttpRequestValues.Builder builder = HttpRequestValues.builder().setUriTemplate("/api/demo");

        processor.process(method(), new org.springframework.core.MethodParameter[0], new Object[0], builder);

        assertTrue(builder.build().getHeaders().isEmpty());
    }

    @Test
    void keepsMultiValueHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Traffic", "gray");
        request.addHeader("X-Traffic", "beta");
        bind(request);

        HttpRequestValues values = invokeProcessor();

        assertEquals(List.of("gray", "beta"), values.getHeaders().get("X-Traffic"));
    }

    private void bindRequest(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(name, value);
        bind(request);
    }

    private void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private HttpRequestValues invokeProcessor() throws Exception {
        HttpRequestValues.Builder builder = HttpRequestValues.builder().setUriTemplate("/api/demo");
        invokeProcessor(builder);
        return builder.build();
    }

    private void invokeProcessor(HttpRequestValues.Builder builder) throws Exception {
        processor.process(method(), new org.springframework.core.MethodParameter[0], new Object[0], builder);
    }

    private Method method() throws NoSuchMethodException {
        return DemoClient.class.getDeclaredMethod("call");
    }

    interface DemoClient {
        void call();
    }
}

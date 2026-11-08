package xyz.wewin.autumn.cloud.extension.propagation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.service.invoker.HttpRequestValues;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * servlet 场景的透传实现。
 *
 * <p>挂在 {@code HttpServiceProxyFactory.Builder#httpRequestValuesProcessor} 上，
 * 在参数解析器之后、请求构建之前执行，因此：</p>
 * <ul>
 *   <li>方法上显式声明的 {@code @RequestHeader} 优先，不会被覆盖；</li>
 *   <li>本处理器只补"没传的"业务 header；</li>
 *   <li>它跑在底层 {@code RestClient} 的拦截器之前，链路追踪中间件随后注入
 *       {@code traceparent} 等 header 时仍是最终值。</li>
 * </ul>
 *
 * <p>非 servlet 线程（{@code @Async}、定时任务、线程池）里 {@code RequestContextHolder}
 * 为空时直接跳过，不抛异常。</p>
 */
public final class ServletHeaderPropagationProcessor implements HttpRequestValues.Processor {

    private final HeaderPropagation propagation;

    public ServletHeaderPropagationProcessor(HeaderPropagation propagation) {
        this.propagation = Objects.requireNonNull(propagation, "propagation must not be null");
    }

    @Override
    public void process(Method method, MethodParameter[] parameters, Object[] arguments,
                        HttpRequestValues.Builder builder) {
        HeaderPropagationPolicy policy = propagation.policy();
        if (!policy.isEnabled()) {
            return;
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        builder.configureHeaders(target -> {
            List<String> selected = policy.select(Collections.list(request.getHeaderNames()), target.headerNames());
            for (String name : selected) {
                for (String value : Collections.list(request.getHeaders(name))) {
                    target.add(name, value);
                }
            }
        });
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest()
                : null;
    }
}

package xyz.wewin.autumn.cloud.extension.propagation.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import xyz.wewin.autumn.cloud.extension.propagation.HeaderPropagation;
import xyz.wewin.autumn.cloud.extension.propagation.HeaderPropagationProperties;

/**
 * servlet（RestClient）侧的自动装配：把透传处理器挂到所有
 * {@code @ImportHttpServices} 声明的声明式客户端上。
 *
 * <p>这里只装配"扩展点"，不决定"传不传"：开关与白名单仍然由
 * {@code autumn.propagation.*} 控制，且每次调用现读配置，所以配置中心刷新后
 * 开关立即生效，不需要重启。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(HeaderPropagationProperties.class)
public class HeaderPropagationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HeaderPropagation headerPropagation(ObjectProvider<HeaderPropagationProperties> properties) {
        // 用 supplier 而不是直接引用实例：@ConfigurationProperties 重新绑定后能拿到新实例
        return HeaderPropagation.of(properties::getObject);
    }

    /**
     * {@code @ImportHttpServices} 的 RestClient 分组统一挂 Processor。
     *
     * <p>Processor 从当前入站请求取 header，只补没有的、不覆盖方法上
     * {@code @RequestHeader} 已声明的同名 header。</p>
     */
    @Bean
    public RestClientHttpServiceGroupConfigurer headerPropagationRestClientGroupConfigurer(
            HeaderPropagation propagation) {
        return groups -> groups.forEachProxyFactory((group, factoryBuilder) ->
                factoryBuilder.httpRequestValuesProcessor(propagation.processor()));
    }

    /**
     * WebFlux 里当前请求不在 ThreadLocal 里，只能从 Reactor Context 取。
     * 框架提供的 {@link ServerWebExchangeContextFilter} 才会把 exchange 写进 context，
     * 所以 reactive 应用需要它；应用自己注册过就别重复注册。
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnMissingBean
    public ServerWebExchangeContextFilter serverWebExchangeContextFilter() {
        return new ServerWebExchangeContextFilter();
    }
}

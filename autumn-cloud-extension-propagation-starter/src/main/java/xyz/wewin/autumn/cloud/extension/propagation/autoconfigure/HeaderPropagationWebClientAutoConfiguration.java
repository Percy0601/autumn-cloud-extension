package xyz.wewin.autumn.cloud.extension.propagation.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.support.WebClientHttpServiceGroupConfigurer;
import xyz.wewin.autumn.cloud.extension.propagation.HeaderPropagation;
import xyz.wewin.autumn.cloud.extension.propagation.ReactiveHeaderPropagationFilter;

/**
 * reactive（WebClient）侧的自动装配：给 {@code @ImportHttpServices} 的 WebClient
 * 分组统一加透传过滤器。
 *
 * <p>单独成类并用类名做条件，是为了在只有 MVC、没有 spring-webflux 的应用里
 * 整个类都不被加载，不会因为引用 {@code WebClient} 而报 NoClassDefFoundError。</p>
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.web.reactive.function.client.WebClient")
public class HeaderPropagationWebClientAutoConfiguration {

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.reactive.function.client.support.WebClientHttpServiceGroupConfigurer")
    public WebClientHttpServiceGroupConfigurer headerPropagationWebClientGroupConfigurer(
            HeaderPropagation propagation) {
        ReactiveHeaderPropagationFilter filter = new ReactiveHeaderPropagationFilter(propagation);
        return groups -> groups.forEachClient((group, builder) -> builder.filter(filter));
    }
}

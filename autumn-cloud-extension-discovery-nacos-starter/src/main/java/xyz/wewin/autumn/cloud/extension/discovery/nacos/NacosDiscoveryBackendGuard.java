package xyz.wewin.autumn.cloud.extension.discovery.nacos;

import xyz.wewin.autumn.cloud.extension.core.support.BackendValidator;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** 与具体后端选择无关的校验：backend 拼错、漏引 starter、多 starter 未指定时给出明确报错。 */
@AutoConfiguration
public class NacosDiscoveryBackendGuard {

    @Bean
    static BeanFactoryPostProcessor nacosDiscoveryBackendValidation(Environment environment) {
        return beanFactory -> BackendValidator.validateDiscovery(
                environment.getProperty(BackendValidator.DISCOVERY_BACKEND),
                NacosDiscoveryBackendGuard.class.getClassLoader());
    }
}

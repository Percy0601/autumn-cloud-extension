package xyz.wewin.autumn.cloud.extension.config.nacos;

import xyz.wewin.autumn.cloud.extension.core.support.BackendValidator;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** 校验配置中心 backend：拼错 / 漏引 starter 时 fail-fast，未配置时给出明确提示。 */
@AutoConfiguration
public class NacosConfigBackendGuard {

    @Bean
    static BeanFactoryPostProcessor nacosConfigBackendValidation(Environment environment) {
        return beanFactory -> BackendValidator.validateConfig(
                environment.getProperty(BackendValidator.CONFIG_BACKEND),
                NacosConfigBackendGuard.class.getClassLoader());
    }
}

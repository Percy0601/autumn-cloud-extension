package com.autumn.cloud.extension.config.consul;

import com.autumn.cloud.extension.core.support.BackendValidator;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** 校验配置中心 backend：拼错 / 漏引 starter 时 fail-fast，未配置时给出明确提示。 */
@AutoConfiguration
public class ConsulConfigBackendGuard {

    @Bean
    static BeanFactoryPostProcessor consulConfigBackendValidation(Environment environment) {
        return beanFactory -> BackendValidator.validateConfig(
                environment.getProperty(BackendValidator.CONFIG_BACKEND),
                ConsulConfigBackendGuard.class.getClassLoader());
    }
}

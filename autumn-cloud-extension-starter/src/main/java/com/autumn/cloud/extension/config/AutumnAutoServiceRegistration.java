package com.autumn.cloud.extension.config;

import org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.ApplicationContext;

/**
 * Web 容器就绪后自动注册（对应 spring-cloud-consul 的 ConsulAutoServiceRegistration）。
 */
public class AutumnAutoServiceRegistration extends AbstractAutoServiceRegistration<AutumnRegistration> {

    private final AutumnRegistration registration;

    public AutumnAutoServiceRegistration(ApplicationContext context,
                                         ServiceRegistry<AutumnRegistration> serviceRegistry,
                                         AutumnRegistration registration) {
        super(context, serviceRegistry, new AutoServiceRegistrationProperties());
        this.registration = registration;
    }

    @Override
    protected Object getConfiguration() {
        return registration;
    }

    @Override
    protected boolean isEnabled() {
        return true;
    }

    @Override
    protected AutumnRegistration getRegistration() {
        return registration;
    }

    @Override
    protected AutumnRegistration getManagementRegistration() {
        return null;
    }
}

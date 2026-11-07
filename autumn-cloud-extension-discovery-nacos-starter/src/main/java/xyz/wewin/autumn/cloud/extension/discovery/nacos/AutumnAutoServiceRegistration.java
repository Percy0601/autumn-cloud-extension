package xyz.wewin.autumn.cloud.extension.discovery.nacos;

import org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.ApplicationContext;

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

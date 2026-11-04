package com.autumn.cloud.extension.discovery.multicast;

import com.autumn.cloud.extension.core.model.InstanceRegistration;

import java.net.URI;
import java.util.Map;

public final class AutumnRegistration implements org.springframework.cloud.client.serviceregistry.Registration {

    private final InstanceRegistration delegate;

    public AutumnRegistration(InstanceRegistration delegate) {
        this.delegate = delegate;
    }

    public InstanceRegistration getInstanceRegistration() {
        return delegate;
    }

    @Override
    public String getServiceId() {
        return delegate.service();
    }

    @Override
    public String getHost() {
        return delegate.host();
    }

    @Override
    public int getPort() {
        return delegate.port();
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public URI getUri() {
        return URI.create("http://" + delegate.host() + ":" + delegate.port());
    }

    @Override
    public Map<String, String> getMetadata() {
        return delegate.metadata();
    }

    public String getInstanceId() {
        return delegate.instanceId();
    }
}

package com.autumn.cloud.extension.multicast;

import com.autumn.cloud.extension.config.AutumnRegistration;
import com.autumn.cloud.extension.core.multicast.MulticastDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

import java.io.IOException;

/**
 * 组播注册生命周期：加入组播组并广播自身注册。
 */
public class MulticastServiceRegistry implements ServiceRegistry<AutumnRegistration> {

    private static final Logger log = LoggerFactory.getLogger(MulticastServiceRegistry.class);

    private final MulticastDiscovery discovery;

    public MulticastServiceRegistry(MulticastDiscovery discovery) {
        this.discovery = discovery;
    }

    @Override
    public void register(AutumnRegistration registration) {
        try {
            discovery.start();
        } catch (IOException e) {
            throw new IllegalStateException("multicast start failed: " + e.getMessage(), e);
        }
        discovery.publish(registration.getInstanceRegistration());
        log.info("Published via multicast: {} ({}:{}) traffic={}",
                registration.getServiceId(), registration.getHost(), registration.getPort(),
                registration.getMetadata().get("traffic"));
    }

    @Override
    public void deregister(AutumnRegistration registration) {
        close();
    }

    @Override
    public void close() {
        discovery.close();
    }

    @Override
    public void setStatus(AutumnRegistration registration, String status) {
        throw new UnsupportedOperationException("setStatus is not supported");
    }

    @Override
    public <T> T getStatus(AutumnRegistration registration) {
        throw new UnsupportedOperationException("getStatus is not supported");
    }
}

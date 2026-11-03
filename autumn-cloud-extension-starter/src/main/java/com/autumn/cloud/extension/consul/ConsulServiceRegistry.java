package com.autumn.cloud.extension.consul;

import com.autumn.cloud.extension.config.AutumnRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consul 注册生命周期：注册 + TTL 周期续约 + 停机反注册。
 */
public class ConsulServiceRegistry implements ServiceRegistry<AutumnRegistration> {

    private static final Logger log = LoggerFactory.getLogger(ConsulServiceRegistry.class);

    private final ConsulClient client;
    private final ConsulProperties properties;
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private volatile ScheduledExecutorService scheduler;
    private volatile AutumnRegistration current;

    public ConsulServiceRegistry(ConsulClient client, ConsulProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void register(AutumnRegistration registration) {
        current = registration;
        client.register(registration.getInstanceRegistration());
        registered.set(true);
        long passIntervalMs = Math.max(properties.healthCheckTtlSeconds() * 1000L / 3, 1000);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "autumn-consul-ttl");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::passCheck, passIntervalMs, passIntervalMs, TimeUnit.MILLISECONDS);
        this.scheduler = scheduler;
        log.info("Registered in Consul: {} ({}:{}) trafficLabel={}",
                registration.getServiceId(), registration.getHost(), registration.getPort(),
                registration.getMetadata().get("traffic-label"));
    }

    @Override
    public void deregister(AutumnRegistration registration) {
        if (registered.compareAndSet(true, false)) {
            stopScheduler();
            client.deregister(registration.getInstanceId());
            log.info("Deregistered from Consul: {}", registration.getInstanceId());
        }
    }

    @Override
    public void close() {
        stopScheduler();
    }

    @Override
    public void setStatus(AutumnRegistration registration, String status) {
        throw new UnsupportedOperationException("setStatus is not supported");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getStatus(AutumnRegistration registration) {
        throw new UnsupportedOperationException("getStatus is not supported");
    }

    private void passCheck() {
        AutumnRegistration registration = current;
        if (registration != null) {
            try {
                client.passCheck(registration.getInstanceId());
            } catch (Exception e) {
                log.warn("Consul TTL pass failed for {}: {}", registration.getInstanceId(), e.getMessage());
            }
        }
    }

    private void stopScheduler() {
        ScheduledExecutorService scheduler = this.scheduler;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}

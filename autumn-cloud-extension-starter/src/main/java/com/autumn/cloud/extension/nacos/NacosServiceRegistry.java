package com.autumn.cloud.extension.nacos;

import com.autumn.cloud.extension.config.AutumnRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos 注册生命周期：注册 + 临时实例心跳 + 停机反注册。
 */
public class NacosServiceRegistry implements ServiceRegistry<AutumnRegistration> {

    private static final Logger log = LoggerFactory.getLogger(NacosServiceRegistry.class);

    private final NacosClient client;
    private final NacosProperties properties;
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private volatile ScheduledExecutorService scheduler;
    private volatile AutumnRegistration current;

    public NacosServiceRegistry(NacosClient client, NacosProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void register(AutumnRegistration registration) {
        current = registration;
        client.register(registration.getInstanceRegistration());
        registered.set(true);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "autumn-nacos-beat");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::beat, properties.beatIntervalMs(), properties.beatIntervalMs(),
                TimeUnit.MILLISECONDS);
        this.scheduler = scheduler;
        log.info("Registered in Nacos: {} ({}:{}) trafficLabel={}",
                registration.getServiceId(), registration.getHost(), registration.getPort(),
                registration.getMetadata().get("traffic-label"));
    }

    @Override
    public void deregister(AutumnRegistration registration) {
        if (registered.compareAndSet(true, false)) {
            stopScheduler();
            client.deregister(registration.getInstanceRegistration());
            log.info("Deregistered from Nacos: {}", registration.getInstanceId());
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
    public <T> T getStatus(AutumnRegistration registration) {
        throw new UnsupportedOperationException("getStatus is not supported");
    }

    private void beat() {
        AutumnRegistration registration = current;
        if (registration != null) {
            try {
                client.heartbeat(registration.getInstanceRegistration());
            } catch (Exception e) {
                log.warn("Nacos beat failed for {}: {}", registration.getInstanceId(), e.getMessage());
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

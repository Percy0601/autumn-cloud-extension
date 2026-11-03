package com.autumn.cloud.extension.multicast;

import com.autumn.cloud.extension.core.multicast.MulticastDiscovery;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;

/**
 * 组播生命周期：无论是否注册自身（autumn.discovery.register），
 * 只要选择 multicast 后端就加入组播组接收实例广播（网关场景必需）。
 */
public class MulticastDiscoveryLifecycle implements SmartLifecycle {

    private final MulticastDiscovery discovery;
    private volatile boolean running;

    public MulticastDiscoveryLifecycle(MulticastDiscovery discovery) {
        this.discovery = discovery;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            discovery.start();
            running = true;
        } catch (IOException e) {
            throw new IllegalStateException("multicast start failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        if (running) {
            running = false;
            discovery.close();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

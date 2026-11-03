package com.autumn.cloud.extension.config;

import com.autumn.cloud.extension.core.trafficlabel.TrafficLabel;
import com.autumn.cloud.extension.core.model.InstanceRegistration;
import com.autumn.cloud.extension.core.util.Networks;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

/**
 * 从配置 + 环境构建注册信息。
 */
public final class Registrations {

    private Registrations() {
    }

    public static InstanceRegistration build(AutumnDiscoveryProperties properties, Environment environment) {
        String service = firstNonBlank(properties.service(),
                environment.getProperty("spring.application.name"), "application");
        String host = properties.host() != null && !properties.host().isBlank()
                ? properties.host().trim()
                : Networks.localHostAddress();
        int port = properties.port() != null
                ? properties.port()
                : environment.getProperty("server.port", Integer.class, 8080);

        Map<String, String> metadata = new HashMap<>(properties.metadata());
        if (properties.trafficLabel() != null && !properties.trafficLabel().isBlank()) {
            metadata.put(TrafficLabel.META_KEY, properties.trafficLabel().trim());
        }
        String instanceId = service + "-" + host + ":" + port;
        return new InstanceRegistration(service, instanceId, host, port, metadata);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}

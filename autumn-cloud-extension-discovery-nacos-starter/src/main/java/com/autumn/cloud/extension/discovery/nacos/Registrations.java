package com.autumn.cloud.extension.discovery.nacos;

import com.autumn.cloud.extension.core.model.InstanceRegistration;
import com.autumn.cloud.extension.core.traffic.Traffic;
import com.autumn.cloud.extension.core.util.Networks;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

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
        if (properties.traffic() != null && !properties.traffic().isBlank()) {
            metadata.put(Traffic.META_KEY, properties.traffic().trim());
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

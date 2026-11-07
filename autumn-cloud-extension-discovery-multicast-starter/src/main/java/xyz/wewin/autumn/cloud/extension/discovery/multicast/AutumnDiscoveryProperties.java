package xyz.wewin.autumn.cloud.extension.discovery.multicast;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

@ConfigurationProperties(prefix = "autumn.discovery")
public record AutumnDiscoveryProperties(
        @DefaultValue("true") boolean register,
        String service,
        String host,
        Integer port,
        String traffic,
        Map<String, String> metadata) {

    public AutumnDiscoveryProperties {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}

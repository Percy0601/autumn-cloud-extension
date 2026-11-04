package com.autumn.cloud.extension.discovery.multicast;

import com.autumn.cloud.extension.core.traffic.Traffic;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "autumn.traffic")
public record TrafficProperties(
        @DefaultValue(Traffic.DEFAULT_HEADER) String header,
        @DefaultValue("true") boolean fallbackToMain) {
}

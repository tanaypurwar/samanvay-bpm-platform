package com.samanvay.tasklist.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection to the Samanvay BPM engine REST API. */
@ConfigurationProperties("samanvay.engine")
public record EngineProperties(
    String baseUrl,
    String username,
    String password,
    Duration connectTimeout,
    Duration readTimeout) {
}

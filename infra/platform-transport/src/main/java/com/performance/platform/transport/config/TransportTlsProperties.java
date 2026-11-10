package com.performance.platform.transport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("transport.tls")
public record TransportTlsProperties(boolean enabled, String bundle, ClientAuth clientAuth) {
    public enum ClientAuth { NONE, NEED }

    public TransportTlsProperties {
        clientAuth = clientAuth == null ? ClientAuth.NEED : clientAuth;
        if (enabled && (bundle == null || bundle.isBlank())) {
            throw new IllegalArgumentException("transport.tls.bundle is required when TLS is enabled");
        }
    }
}

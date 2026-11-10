package com.performance.platform.transport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("transport.http.listener")
public record HttpListenerProperties(@DefaultValue("true") boolean enabled, String bindAddress,
                                     @DefaultValue("8091") int port,
                                     String advertisedUrl, String orchestratorUrl) {
    public HttpListenerProperties {
        bindAddress = bindAddress == null ? "0.0.0.0" : bindAddress;
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("HTTP transport listener port must be between 0 and 65535");
        }
    }

    public static HttpListenerProperties disabled() {
        return new HttpListenerProperties(false, "127.0.0.1", 0, null, null);
    }
}

package com.performance.platform.transport.config;

import com.performance.platform.transport.tls.TransportTls;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TransportTlsProperties.class)
public class TransportTlsConfiguration {

    @Bean
    public TransportTls transportTls(TransportTlsProperties properties, ObjectProvider<SslBundles> bundles) {
        if (!properties.enabled()) {
            return TransportTls.disabled();
        }
        return new TransportTls(bundles.getObject().getBundle(properties.bundle()),
                properties.clientAuth() == TransportTlsProperties.ClientAuth.NEED);
    }
}

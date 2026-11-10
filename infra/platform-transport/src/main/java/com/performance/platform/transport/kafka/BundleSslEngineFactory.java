package com.performance.platform.transport.kafka;

import com.performance.platform.transport.tls.TransportTls;
import org.apache.kafka.common.security.auth.SslEngineFactory;
import org.springframework.boot.ssl.SslBundle;

import javax.net.ssl.SSLEngine;
import java.security.KeyStore;
import java.util.Map;
import java.util.Set;

public final class BundleSslEngineFactory implements SslEngineFactory {

    public static final String BUNDLE_CONFIG = "platform.transport.ssl.bundle";
    private TransportTls tls;

    @Override
    public void configure(Map<String, ?> configs) {
        if (!(configs.get(BUNDLE_CONFIG) instanceof SslBundle bundle)) {
            throw new IllegalArgumentException("Kafka transport requires a Spring SSL bundle");
        }
        tls = new TransportTls(bundle, false);
    }

    @Override
    public SSLEngine createClientSslEngine(String host, int port, String endpointIdentification) {
        var engine = tls.context().createSSLEngine(host, port);
        engine.setUseClientMode(true);
        engine.setSSLParameters(tls.parameters(true));
        return engine;
    }

    @Override
    public SSLEngine createServerSslEngine(String host, int port) {
        throw new UnsupportedOperationException("Kafka transport is a broker client only");
    }

    @Override
    public boolean shouldBeRebuilt(Map<String, Object> configs) {
        return configs.get(BUNDLE_CONFIG) != tls.bundle();
    }

    @Override
    public Set<String> reconfigurableConfigs() {
        return Set.of(BUNDLE_CONFIG);
    }

    @Override
    public KeyStore keystore() {
        return tls.bundle().getStores().getKeyStore();
    }

    @Override
    public KeyStore truststore() {
        return tls.bundle().getStores().getTrustStore();
    }

    @Override
    public void close() {
    }
}

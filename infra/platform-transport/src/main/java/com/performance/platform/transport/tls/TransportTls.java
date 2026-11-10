package com.performance.platform.transport.tls;

import org.springframework.boot.ssl.SslBundle;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.security.KeyStoreException;
import java.util.Collections;

public final class TransportTls {

    private final SslBundle bundle;
    private final boolean mutual;
    private final SSLContext context;

    public TransportTls(SslBundle bundle, boolean mutual) {
        this.bundle = bundle;
        this.mutual = mutual;
        if (bundle != null) {
            if (bundle.getStores().getTrustStore() == null) {
                throw new IllegalArgumentException("Transport SSL bundle requires an explicit truststore/CA");
            }
            if (mutual) {
                requireIdentity();
            }
        }
        this.context = bundle == null ? null : bundle.createSslContext();
    }

    public static TransportTls disabled() {
        return new TransportTls(null, false);
    }

    public boolean enabled() {
        return bundle != null;
    }

    public boolean mutual() {
        return mutual;
    }

    public SslBundle bundle() {
        return bundle;
    }

    public SSLContext context() {
        return context;
    }

    public void requireIdentity() {
        try {
            var store = bundle.getStores().getKeyStore();
            if (store == null || Collections.list(store.aliases()).stream().noneMatch(alias -> {
                try {
                    return store.isKeyEntry(alias);
                } catch (KeyStoreException e) {
                    throw new IllegalArgumentException("Cannot read transport identity", e);
                }
            })) {
                throw new IllegalArgumentException("Transport SSL bundle requires a certificate and private key");
            }
        } catch (KeyStoreException e) {
            throw new IllegalArgumentException("Cannot read transport identity", e);
        }
    }

    public SSLParameters parameters(boolean client) {
        var parameters = context.getDefaultSSLParameters();
        var options = bundle.getOptions();
        if (options.getEnabledProtocols() != null) {
            parameters.setProtocols(options.getEnabledProtocols());
        }
        if (options.getCiphers() != null) {
            parameters.setCipherSuites(options.getCiphers());
        }
        if (client) {
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
        } else {
            parameters.setNeedClientAuth(mutual);
        }
        return parameters;
    }
}

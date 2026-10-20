package com.performance.platform.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Facade Spring de {@link RuntimeConfigResolver}.
 * <p>
 * Conserve l'API historique ({@code resolveMode()}, {@code resolveRole()},
 * {@code resolveTransportType()}) pour les beans, mais delegue toute la
 * logique (constantes, priorite, parsing) a la source unique
 * {@link RuntimeConfigResolver}.
 * <p>
 * Priorite (ADR-006) : env var > argument CLI > property > defaut.
 */
@Component
public class RuntimeModeResolver {

    private static final Logger log = LoggerFactory.getLogger(RuntimeModeResolver.class);

    static final String ENV_RUNTIME_MODE = RuntimeConfigResolver.ENV_RUNTIME_MODE;
    static final String ENV_MODE = RuntimeConfigResolver.ENV_MODE;
    static final String ENV_TRANSPORT_TYPE = RuntimeConfigResolver.ENV_TRANSPORT_TYPE;

    static final String PROP_RUNTIME_MODE = RuntimeConfigResolver.PROP_RUNTIME_MODE;
    static final String PROP_RUNTIME_ROLE = RuntimeConfigResolver.PROP_RUNTIME_ROLE;
    static final String PROP_TRANSPORT_TYPE = RuntimeConfigResolver.PROP_TRANSPORT_TYPE;

    private final RuntimeConfigResolver delegate;

    public RuntimeModeResolver(Environment environment) {
        Objects.requireNonNull(environment, "environment must not be null");
        this.delegate = new RuntimeConfigResolver(environment);
    }

    public RuntimeMode resolveMode() {
        RuntimeMode mode = delegate.resolveMode(null);
        log.info("action=resolve_mode mode={}", mode);
        return mode;
    }

    public RuntimeRole resolveRole() {
        RuntimeRole role = delegate.resolveRole(null);
        log.info("action=resolve_role role={}", role);
        return role;
    }

    public String resolveTransportType() {
        String transport = delegate.resolveTransportType(null);
        log.info("action=resolve_transport transport={}", transport);
        return transport;
    }

    static RuntimeMode parseMode(String value) {
        return RuntimeConfigResolver.parseMode(value);
    }

    static RuntimeRole parseRole(String value) {
        return RuntimeConfigResolver.parseRole(value);
    }
}

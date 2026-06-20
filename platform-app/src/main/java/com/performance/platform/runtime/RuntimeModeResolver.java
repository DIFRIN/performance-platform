package com.performance.platform.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Resout le mode et le role d'execution avec la priorite
 * env var > property (ADR-006).
 * <p>
 * Regles de priorite :
 * <ul>
 *   <li>{@code RUNTIME_MODE} env var > {@code runtime.mode} property</li>
 *   <li>{@code MODE} env var > {@code runtime.role} property</li>
 *   <li>{@code TRANSPORT_TYPE} env var > {@code transport.type} property</li>
 * </ul>
 * <p>
 * Les valeurs par defaut sont {@link RuntimeMode#LOCAL} et
 * {@link RuntimeRole#NONE}.
 */
@Component
public class RuntimeModeResolver {

    private static final Logger log = LoggerFactory.getLogger(RuntimeModeResolver.class);

    static final String ENV_RUNTIME_MODE = "RUNTIME_MODE";
    static final String ENV_MODE = "MODE";
    static final String ENV_TRANSPORT_TYPE = "TRANSPORT_TYPE";

    static final String PROP_RUNTIME_MODE = "runtime.mode";
    static final String PROP_RUNTIME_ROLE = "runtime.role";
    static final String PROP_TRANSPORT_TYPE = "transport.type";

    private final Environment environment;

    public RuntimeModeResolver(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    /**
     * Resout le mode d'execution.
     * <p>
     * Priorite : {@code RUNTIME_MODE} env var > {@code runtime.mode} property.
     * Defaut : {@link RuntimeMode#LOCAL}.
     */
    public RuntimeMode resolveMode() {
        String env = environment.getProperty(ENV_RUNTIME_MODE);
        if (env != null && !env.isBlank()) {
            RuntimeMode mode = parseMode(env);
            log.info("action=resolve_mode source=env RUNTIME_MODE={} mode={}", env, mode);
            return mode;
        }
        String prop = environment.getProperty(PROP_RUNTIME_MODE);
        if (prop != null && !prop.isBlank()) {
            RuntimeMode mode = parseMode(prop);
            log.info("action=resolve_mode source=property {}={} mode={}",
                    PROP_RUNTIME_MODE, prop, mode);
            return mode;
        }
        log.info("action=resolve_mode mode=LOCAL (default)");
        return RuntimeMode.LOCAL;
    }

    /**
     * Resout le role de l'instance.
     * <p>
     * Priorite : {@code MODE} env var > {@code runtime.role} property.
     * Defaut : {@link RuntimeRole#NONE}.
     */
    public RuntimeRole resolveRole() {
        String env = environment.getProperty(ENV_MODE);
        if (env != null && !env.isBlank()) {
            RuntimeRole role = parseRole(env);
            log.info("action=resolve_role source=env MODE={} role={}", env, role);
            return role;
        }
        String prop = environment.getProperty(PROP_RUNTIME_ROLE);
        if (prop != null && !prop.isBlank()) {
            RuntimeRole role = parseRole(prop);
            log.info("action=resolve_role source=property {}={} role={}",
                    PROP_RUNTIME_ROLE, prop, role);
            return role;
        }
        log.info("action=resolve_role role=NONE (default)");
        return RuntimeRole.NONE;
    }

    /**
     * Resout le type de transport.
     * <p>
     * Priorite : {@code TRANSPORT_TYPE} env var > {@code transport.type} property.
     * Defaut : {@code null} (laisse le choix a l'auto-configuration Spring).
     */
    public String resolveTransportType() {
        String env = environment.getProperty(ENV_TRANSPORT_TYPE);
        if (env != null && !env.isBlank()) {
            log.info("action=resolve_transport source=env TRANSPORT_TYPE={}", env);
            return env.strip();
        }
        String prop = environment.getProperty(PROP_TRANSPORT_TYPE);
        if (prop != null && !prop.isBlank()) {
            log.info("action=resolve_transport source=property {}={}",
                    PROP_TRANSPORT_TYPE, prop);
            return prop.strip();
        }
        log.info("action=resolve_transport transport=null (default, auto-configure)");
        return null;
    }

    // ---- Parsing helpers ----

    static RuntimeMode parseMode(String value) {
        try {
            return RuntimeMode.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid runtime mode: '" + value + "'. Expected LOCAL or DISTRIBUTED.");
        }
    }

    static RuntimeRole parseRole(String value) {
        try {
            return RuntimeRole.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid runtime role: '" + value + "'. Expected ORCHESTRATOR, AGENT, or NONE.");
        }
    }
}

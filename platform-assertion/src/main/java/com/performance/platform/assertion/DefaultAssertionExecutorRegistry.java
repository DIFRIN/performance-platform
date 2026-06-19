package com.performance.platform.assertion;

import com.performance.platform.plugin.AssertionExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation par defaut de {@link AssertionExecutorRegistry}.
 * Collecte automatiquement tous les beans {@link AssertionExecutor} via injection Spring.
 * <p>
 * Thread-safe : utilise {@link ConcurrentHashMap} pour le stockage.
 * La resolution se fait par {@link AssertionExecutor#getSupportedAssertionName()}
 * sans jamais utiliser de if/switch sur un type.
 */
@Component
public class DefaultAssertionExecutorRegistry implements AssertionExecutorRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultAssertionExecutorRegistry.class);

    private final Map<String, AssertionExecutor> executors = new ConcurrentHashMap<>();

    /**
     * Constructeur avec injection Spring de tous les {@link AssertionExecutor} disponibles.
     * Chaque executor est automatiquement enregistre via {@link #register(AssertionExecutor)}.
     *
     * @param executors la liste de tous les AssertionExecutor beans Spring (peut etre vide)
     */
    public DefaultAssertionExecutorRegistry(List<AssertionExecutor> executors) {
        executors.forEach(this::register);
        log.info("action=assertion_registry_initialized assertionNames={} count={}",
                getSupportedAssertionNames(), getSupportedAssertionNames().size());
    }

    @Override
    public void register(AssertionExecutor executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        String assertionName = executor.getSupportedAssertionName();
        Objects.requireNonNull(assertionName,
                "getSupportedAssertionName() must not return null for executor "
                + executor.getClass().getName());

        AssertionExecutor previous = executors.put(assertionName, executor);
        if (previous != null) {
            log.warn("action=assertion_executor_replaced assertionName={} previous={} new={}",
                    assertionName, previous.getClass().getSimpleName(),
                    executor.getClass().getSimpleName());
        } else {
            log.info("action=assertion_executor_registered assertionName={} executor={}",
                    assertionName, executor.getClass().getSimpleName());
        }
    }

    @Override
    public AssertionExecutor getFor(String assertionName)
            throws UnsupportedAssertionNameException {
        AssertionExecutor executor = executors.get(assertionName);
        if (executor == null) {
            throw new UnsupportedAssertionNameException(assertionName);
        }
        return executor;
    }

    @Override
    public Set<String> getSupportedAssertionNames() {
        return Set.copyOf(executors.keySet());
    }
}

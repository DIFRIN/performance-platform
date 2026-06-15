package com.performance.platform.infrastructure.executor;

import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation par defaut de {@link TaskExecutorRegistry}.
 * Collecte automatiquement tous les beans {@link TaskExecutor} via injection Spring.
 *
 * <p>Thread-safe : utilise {@link ConcurrentHashMap} pour le stockage.
 * La resolution se fait par {@link TaskExecutor#getSupportedTaskName()} sans
 * jamais utiliser de if/switch sur un type.</p>
 */
@Component
public class DefaultTaskExecutorRegistry implements TaskExecutorRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskExecutorRegistry.class);

    private final Map<String, TaskExecutor> executors = new ConcurrentHashMap<>();

    /**
     * Constructeur avec injection Spring de tous les {@link TaskExecutor} disponibles.
     * Chaque executor est automatiquement enregistre via {@link #register(TaskExecutor)}.
     *
     * @param executors la liste de tous les TaskExecutor beans Spring (peut etre vide)
     */
    public DefaultTaskExecutorRegistry(List<TaskExecutor> executors) {
        executors.forEach(this::register);
        log.info("action=registry_initialized taskNames={} count={}",
                getSupportedTaskNames(), getSupportedTaskNames().size());
    }

    @Override
    public void register(TaskExecutor executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        String taskName = executor.getSupportedTaskName();
        Objects.requireNonNull(taskName, "getSupportedTaskName() must not return null for executor " + executor.getClass().getName());

        TaskExecutor previous = executors.put(taskName, executor);
        if (previous != null) {
            log.warn("action=executor_replaced taskName={} previous={} new={}",
                    taskName, previous.getClass().getSimpleName(), executor.getClass().getSimpleName());
        } else {
            log.info("action=executor_registered taskName={} executor={}",
                    taskName, executor.getClass().getSimpleName());
        }
    }

    @Override
    public TaskExecutor getFor(String taskName) throws UnsupportedTaskNameException {
        TaskExecutor executor = executors.get(taskName);
        if (executor == null) {
            throw new UnsupportedTaskNameException(taskName);
        }
        return executor;
    }

    @Override
    public Set<String> getSupportedTaskNames() {
        return Set.copyOf(executors.keySet());
    }
}

package com.performance.platform.app.config;

import com.performance.platform.engine.local.TaskExecutorLookup;
import com.performance.platform.infrastructure.executor.TaskExecutorRegistry;
import com.performance.platform.infrastructure.executor.UnsupportedTaskNameException;
import com.performance.platform.plugin.TaskExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Implementation de {@link TaskExecutorLookup} adossee au registre unifie
 * {@link TaskExecutorRegistry}.
 *
 * <p>Depuis PDR-035/PDR-036, {@code AssertionExecutor} etend
 * {@code TaskExecutor} : les assertions sont enregistrees dans le meme
 * registre que les taches de preparation et d'injection. Il n'existe plus
 * de second registre ni de methode dediee.</p>
 *
 * <p>Ce composant est la seule glue cross-module necessaire dans
 * {@code platform-app} selon ADR-026.</p>
 */
public final class RegistryTaskExecutorLookup implements TaskExecutorLookup {

    private static final Logger log = LoggerFactory.getLogger(RegistryTaskExecutorLookup.class);

    private final TaskExecutorRegistry taskRegistry;

    public RegistryTaskExecutorLookup(TaskExecutorRegistry taskRegistry) {
        this.taskRegistry = Objects.requireNonNull(taskRegistry, "taskRegistry must not be null");
    }

    @Override
    public TaskExecutor findTaskExecutor(String taskName) {
        try {
            return taskRegistry.getFor(taskName);
        } catch (UnsupportedTaskNameException e) {
            log.debug("action=find_task_executor_not_found taskName={}", taskName);
            return null;
        }
    }
}

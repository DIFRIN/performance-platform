package com.performance.platform.app.config;

import com.performance.platform.assertion.AssertionExecutorRegistry;
import com.performance.platform.assertion.UnsupportedAssertionNameException;
import com.performance.platform.engine.local.TaskExecutorLookup;
import com.performance.platform.infrastructure.executor.TaskExecutorRegistry;
import com.performance.platform.infrastructure.executor.UnsupportedTaskNameException;
import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Implementation de {@link TaskExecutorLookup} qui fait le pont entre
 * les deux registres d'executeurs (taches et assertions).
 *
 * <p>Ce composant est la seule glue cross-module necessaire dans
 * {@code platform-app} selon ADR-026 : il depend de {@code platform-infrastructure}
 * ({@link TaskExecutorRegistry}) et de {@code platform-assertion}
 * ({@link AssertionExecutorRegistry}), deux modules qui ne se connaissent pas.
 *
 * <p>Les exceptions {@link UnsupportedTaskNameException} et
 * {@link UnsupportedAssertionNameException} sont capturees et transformees
 * en {@code null} pour respecter le contrat de {@link TaskExecutorLookup}
 * ("null si non trouve").
 */
public final class RegistryTaskExecutorLookup implements TaskExecutorLookup {

    private static final Logger log = LoggerFactory.getLogger(RegistryTaskExecutorLookup.class);

    private final TaskExecutorRegistry taskRegistry;
    private final AssertionExecutorRegistry assertionRegistry;

    public RegistryTaskExecutorLookup(TaskExecutorRegistry taskRegistry,
                                      AssertionExecutorRegistry assertionRegistry) {
        this.taskRegistry = Objects.requireNonNull(taskRegistry, "taskRegistry must not be null");
        this.assertionRegistry = Objects.requireNonNull(assertionRegistry, "assertionRegistry must not be null");
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

    @Override
    public AssertionExecutor findAssertionExecutor(String assertionName) {
        try {
            return assertionRegistry.getFor(assertionName);
        } catch (UnsupportedAssertionNameException e) {
            log.debug("action=find_assertion_executor_not_found assertionName={}", assertionName);
            return null;
        }
    }
}

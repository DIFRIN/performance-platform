package com.performance.platform.infrastructure.plugin.testfixture;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.TaskExecutor;

import java.time.Duration;
import java.util.Map;

/**
 * Test fixture: a valid plugin TaskExecutor annotated with @Preparation.
 * Used by DefaultPluginLoaderTest to verify successful plugin loading.
 */
@Preparation(name = "test-preparation-plugin", version = "1.0.0", description = "Test preparation plugin")
public class ValidPreparationPlugin implements TaskExecutor {

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        return TaskResult.success(step.id(), getSupportedTaskName(), Duration.ofMillis(1), Map.of());
    }

    @Override
    public String getSupportedTaskName() {
        return "test-preparation-plugin";
    }
}

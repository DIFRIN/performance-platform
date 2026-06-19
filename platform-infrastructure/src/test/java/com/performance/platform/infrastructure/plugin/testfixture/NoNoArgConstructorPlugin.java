package com.performance.platform.infrastructure.plugin.testfixture;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.TaskExecutor;

/**
 * Test fixture: annotated @Preparation but NO no-arg constructor.
 * PluginLoader must report this as a PluginError and skip it.
 */
@Preparation(name = "no-arg-missing-plugin", version = "1.0.0")
public class NoNoArgConstructorPlugin implements TaskExecutor {

    private final String someField;

    // Only constructor requires an argument — no no-arg constructor
    public NoNoArgConstructorPlugin(String someField) {
        this.someField = someField;
    }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        return TaskResult.success(step.id(), getSupportedTaskName(), java.time.Duration.ofMillis(1), java.util.Map.of());
    }

    @Override
    public String getSupportedTaskName() {
        return "no-arg-missing-plugin";
    }
}

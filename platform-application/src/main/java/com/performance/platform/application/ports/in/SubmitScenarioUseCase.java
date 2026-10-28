package com.performance.platform.application.ports.in;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.ScenarioDefinition;

/** Accepts and persists an execution before returning its ID, without waiting for completion. */
public interface SubmitScenarioUseCase {
    ExecutionId submit(ScenarioDefinition scenario);
}

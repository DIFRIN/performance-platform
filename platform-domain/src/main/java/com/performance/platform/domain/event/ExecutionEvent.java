package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;

import java.time.Instant;

/**
 * Événement lié au cycle de vie d'une exécution de scénario.
 */
public sealed interface ExecutionEvent
        permits ScenarioStarted, ScenarioFinished, ScenarioCancelled,
                PhaseStarted, PhaseCompleted,
                ReportGenerated, ReportPublished {

    ExecutionId executionId();
    Instant timestamp();
}

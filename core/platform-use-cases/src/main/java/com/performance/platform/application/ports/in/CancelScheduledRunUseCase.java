package com.performance.platform.application.ports.in;

import com.performance.platform.domain.id.ScheduledRunId;

/** Annule un run planifie encore en attente. */
public interface CancelScheduledRunUseCase {

    void cancel(ScheduledRunId id);
}

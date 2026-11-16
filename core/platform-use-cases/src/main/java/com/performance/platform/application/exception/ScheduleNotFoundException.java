package com.performance.platform.application.exception;

import com.performance.platform.domain.id.ScheduledRunId;

/** Run planifie inconnu. */
public class ScheduleNotFoundException extends RuntimeException {

    private final ScheduledRunId scheduledRunId;

    public ScheduleNotFoundException(ScheduledRunId scheduledRunId) {
        super("No scheduled run with id '" + scheduledRunId.value() + "'");
        this.scheduledRunId = scheduledRunId;
    }

    public ScheduledRunId getScheduledRunId() {
        return scheduledRunId;
    }
}

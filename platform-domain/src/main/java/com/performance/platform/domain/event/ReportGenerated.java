package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'un rapport est genere.
 * Record immuable, 0 annotation framework.
 */
public record ReportGenerated(ExecutionId executionId, ReportId reportId, Instant timestamp) {

    public ReportGenerated {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(reportId, "reportId required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}

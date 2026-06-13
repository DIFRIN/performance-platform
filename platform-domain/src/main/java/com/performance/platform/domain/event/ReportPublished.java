package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;

import java.time.Instant;
import java.util.Objects;

/**
 * Emis lorsqu'un rapport est publie vers une cible externe.
 * Le champ {@code target} est un String opaque — les valeurs concretes
 * (CONFLUENCE, S3, etc.) sont definies dans le module platform-reporting.
 * Record immuable, 0 annotation framework.
 *
 * @param target identifiant opaque de la cible de publication (ex: "CONFLUENCE", "S3")
 */
public record ReportPublished(ExecutionId executionId, ReportId reportId, String target, Instant timestamp) {

    public ReportPublished {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(reportId, "reportId required");
        Objects.requireNonNull(target, "target required");
        Objects.requireNonNull(timestamp, "timestamp required");
    }
}

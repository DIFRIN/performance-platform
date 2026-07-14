package com.performance.platform.domain.assertion;

import com.performance.platform.domain.id.TaskId;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Format de sortie unifie de toutes les assertions.
 * Place dans {@code TaskResult.outputs} sous la cle {@code "assertion"} pour la
 * serialisation via {@code ExecutionEvent}.
 *
 * <p>Record immuable -- 0 annotation framework. Les collections sont copiees
 * defensivement dans le constructeur compact.
 */
public record AssertionSummary(
    TaskId assertionId,
    AssertionStatus verdict,
    String description,
    Map<String, Object> collectedData,
    List<AssertionSample> history,
    Duration evaluationDuration,
    Instant evaluatedAt
) {
    public AssertionSummary {
        Objects.requireNonNull(assertionId, "assertionId required");
        Objects.requireNonNull(verdict, "verdict required");
        Objects.requireNonNull(description, "description required");
        Objects.requireNonNull(evaluationDuration, "evaluationDuration required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt required");
        collectedData = collectedData == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(collectedData));
        history = history == null ? List.of() : List.copyOf(history);
    }

    public boolean isPassed() {
        return verdict == AssertionStatus.PASSED;
    }

    public static AssertionSummary of(
            TaskId assertionId, AssertionStatus verdict, String description,
            Map<String, Object> collectedData, Duration evaluationDuration, Instant evaluatedAt) {
        return new AssertionSummary(assertionId, verdict, description,
                collectedData, List.of(), evaluationDuration, evaluatedAt);
    }
}

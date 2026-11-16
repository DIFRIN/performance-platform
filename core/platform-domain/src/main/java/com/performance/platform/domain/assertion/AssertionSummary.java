package com.performance.platform.domain.assertion;

import com.performance.platform.domain.id.TaskId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Format de sortie unifie de toutes les assertions.
 * Place dans {@code TaskResult.outputs} sous la cle {@code "assertion"}.
 *
 * <p>Le resume porte trois niveaux :
 * <ul>
 *   <li>le {@link SloStatement} textuel (ce que l'assertion pretend garantir) ;</li>
 *   <li>la liste des {@link Check} (verdict machine, seule source du verdict) ;</li>
 *   <li>un statut global calcule par {@link Rollup}, jamais par un adapter.</li>
 * </ul>
 *
 * <p>Record immuable -- 0 annotation framework.
 */
public record AssertionSummary(
    TaskId assertionId,
    AssertionStatus verdict,
    String description,
    List<AssertionSample> history,
    Duration evaluationDuration,
    Instant evaluatedAt,
    AssertionFamily family,
    SloStatement slo,
    List<Check> checks,
    Rollup rollup
) {

    /** Cle contractuelle de l'output contenant l'{@link AssertionSummary}. */
    public static final String OUTPUT_KEY = "assertion";

    public AssertionSummary {
        Objects.requireNonNull(assertionId, "assertionId required");
        Objects.requireNonNull(verdict, "verdict required");
        Objects.requireNonNull(description, "description required");
        Objects.requireNonNull(evaluationDuration, "evaluationDuration required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt required");
        history = history == null ? List.of() : List.copyOf(history);
        family = family == null ? AssertionFamily.CUSTOM : family;
        slo = slo == null ? SloStatement.empty() : slo;
        checks = checks == null ? List.of() : List.copyOf(checks);
        rollup = rollup == null ? Rollup.all() : rollup;
    }

    public boolean isPassed() {
        return verdict == AssertionStatus.PASSED;
    }

    /**
     * Resume sans checks detailles : pour une evaluation opaque (plugin custom,
     * mesure non decomposable). Le verdict enregistre fait foi ; les checks
     * restent la forme recommandee pour tout ce qui est mesurable.
     */
    public static AssertionSummary verdictOnly(TaskId assertionId, AssertionStatus verdict,
                                               String description, Duration evaluationDuration,
                                               Instant evaluatedAt) {
        return new AssertionSummary(assertionId, verdict, description, List.of(),
                evaluationDuration, evaluatedAt, AssertionFamily.CUSTOM,
                SloStatement.empty(), List.of(), Rollup.all());
    }
}

package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.AssertionFamily;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.Rollup;
import com.performance.platform.domain.assertion.SloStatement;
import com.performance.platform.domain.id.TaskId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Fabrique l'{@link AssertionSummary} d'une assertion multi-checks.
 * <p>
 * Le statut global vient toujours de {@link Rollup#combine(List)}, jamais d'un
 * adapter. Les adapters ne fournissent que des {@link Check}.
 */
public final class AssertionSummaryFactory {

    private static final int DESCRIPTION_LIMIT = 900;

    private AssertionSummaryFactory() {
    }

    /**
     * Construit le resume complet d'une assertion.
     *
     * @return le resume, avec verdict, checks et SLO
     */
    public static AssertionSummary build(TaskId assertionId, AssertionFamily family,
                                         SloStatement slo, Rollup rollup, List<Check> checks,
                                         Duration evaluationDuration, Instant evaluatedAt) {
        Objects.requireNonNull(assertionId, "assertionId required");
        List<Check> safeChecks = checks == null ? List.of() : List.copyOf(checks);
        Rollup safeRollup = rollup == null ? Rollup.all() : rollup;
        AssertionStatus verdict = safeRollup.combine(safeChecks);
        String description = describe(slo, safeChecks, verdict);

        return new AssertionSummary(
                assertionId, verdict, description, List.of(),
                evaluationDuration == null ? Duration.ZERO : evaluationDuration,
                evaluatedAt == null ? Instant.now() : evaluatedAt,
                family == null ? AssertionFamily.CUSTOM : family,
                slo == null ? SloStatement.empty() : slo,
                safeChecks, safeRollup);
    }

    /** Verdict global sans construire de resume. */
    public static AssertionStatus verdict(List<Check> checks, Rollup rollup) {
        return CheckEvaluator.rollup(checks, rollup);
    }

    // ------------------------------------------------------------------

    private static String describe(SloStatement slo, List<Check> checks, AssertionStatus verdict) {
        StringBuilder text = new StringBuilder();
        if (slo != null && !slo.blank()) {
            if (!slo.title().isBlank()) {
                text.append(slo.title()).append(": ");
            }
            if (!slo.slo().isBlank()) {
                text.append(slo.slo());
            }
        }
        if (text.isEmpty()) {
            text.append(verdict);
        }
        if (!checks.isEmpty()) {
            text.append(" [");
            for (int i = 0; i < checks.size(); i++) {
                if (i > 0) {
                    text.append("; ");
                }
                Check check = checks.get(i);
                text.append(check.metric()).append(' ').append(check.status());
                if (check.actualValue() != null) {
                    text.append(" observed=").append(check.actualValue());
                }
            }
            text.append(']');
        }
        String value = text.toString();
        return value.length() <= DESCRIPTION_LIMIT
                ? value
                : value.substring(0, DESCRIPTION_LIMIT - 3) + "...";
    }
}

package com.performance.platform.domain.assertion;

import java.util.Objects;

/**
 * Resultat d'un critere unique. C'est le seul endroit ou un verdict est calcule.
 *
 * @param metric   metrique evaluee
 * @param observed observation (peut etre null si aucune donnee)
 * @param expected attente appliquee (peut etre null pour un critere temporel pur)
 * @param status   statut du critere
 * @param intent   libelle court, lisible dans le rapport
 * @param rationale justification ou raison de l'absence de mesure
 */
public record Check(MetricId metric, Observation observed, Expectation expected,
                    AssertionStatus status, String intent, String rationale) {

    public Check {
        Objects.requireNonNull(metric, "metric required");
        Objects.requireNonNull(status, "status required");
        intent = intent == null ? "" : intent;
        rationale = rationale == null ? "" : rationale;
    }

    public static Check passed(Observation observation, Expectation expectation, String intent) {
        return new Check(observation.metric(), observation, expectation,
                AssertionStatus.PASSED, intent, "");
    }

    public static Check failed(Observation observation, Expectation expectation,
                               String intent, String rationale) {
        return new Check(observation.metric(), observation, expectation,
                AssertionStatus.FAILED, intent, rationale);
    }

    public static Check inconclusive(MetricId metric, String intent, String reason) {
        return new Check(metric, null, null, AssertionStatus.INCONCLUSIVE, intent, reason);
    }

    public static Check skipped(MetricId metric, String intent, String reason) {
        return new Check(metric, null, null, AssertionStatus.SKIPPED, intent, reason);
    }

    public boolean isCriticalFailure(Severity severity) {
        return status == AssertionStatus.FAILED && severity == Severity.CRITICAL;
    }

    /** @return la valeur observee, ou null si aucune mesure. */
    public Double actualValue() {
        return observed == null || !observed.measurable() ? null : observed.value();
    }
}

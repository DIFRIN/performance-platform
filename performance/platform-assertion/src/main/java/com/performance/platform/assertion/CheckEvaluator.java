package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.Expectation;
import com.performance.platform.domain.assertion.MetricId;
import com.performance.platform.domain.assertion.Observation;
import com.performance.platform.domain.assertion.Rollup;

import java.util.List;

/**
 * Seul point de calcul du verdict d'un check a partir d'un seuil.
 * <p>
 * Aucun adapter ne compare une valeur : il produit une {@link Observation},
 * ce moteur applique l'{@link Expectation}. Le libelle ({@code intent}) est
 * genere ici et surchargeable par l'appelant.
 */
public final class CheckEvaluator {

    private CheckEvaluator() {
    }

    /**
     * Evalue une observation par rapport a une attente.
     *
     * @param observation mesure (peut etre null si indisponible)
     * @param expectation attente appliquee
     * @param metric      metrique (utilisee si l'observation manque)
     * @param intent      libelle lisible (peut etre null)
     * @return le check, jamais null
     */
    public static Check evaluate(Observation observation, Expectation expectation,
                                 MetricId metric, String intent) {
        String label = intent == null || intent.isBlank()
                ? defaultIntent(metric, expectation)
                : intent;
        if (observation == null) {
            return Check.inconclusive(metric, label, "No measurement available");
        }
        if (!observation.measurable()) {
            return Check.inconclusive(metric, label, "Measurement is not a finite number");
        }
        boolean satisfied = expectation.isSatisfiedBy(observation.value());
        return satisfied
                ? Check.passed(observation, expectation, label)
                : Check.failed(observation, expectation, label,
                        "Observed " + format(observation.value()) + " "
                                + OperatorResolver.symbol(expectation.operator()) + " "
                                + format(expectation.threshold()) + " expected");
    }

    /** Cree un check INCONCLUSIVE documente. */
    public static Check inconclusive(MetricId metric, String intent, String reason) {
        return Check.inconclusive(metric, intent, reason);
    }

    /** Cree un check SKIPPED (critere non applicable). */
    public static Check skipped(MetricId metric, String intent, String reason) {
        return Check.skipped(metric, intent, reason);
    }

    /** Combine les checks en statut global (delegue a {@link Rollup}). */
    public static AssertionStatus rollup(List<Check> checks, Rollup rollup) {
        return (rollup == null ? Rollup.all() : rollup).combine(checks);
    }

    /** @return vrai si au moins un check est critique et en echec. */
    public static boolean hasCriticalFailure(List<Check> checks,
                                             com.performance.platform.domain.assertion.Severity severity) {
        return checks.stream().anyMatch(check -> check.isCriticalFailure(severity));
    }

    private static String defaultIntent(MetricId metric, Expectation expectation) {
        if (expectation == null) {
            return metric == null ? "" : metric.toString();
        }
        return (metric == null ? "" : metric.toString()) + " "
                + expectation.describe(null).strip();
    }

    private static String format(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }
}

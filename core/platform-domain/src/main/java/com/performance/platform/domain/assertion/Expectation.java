package com.performance.platform.domain.assertion;

import java.util.Objects;

/**
 * Attente typee appliquee a une observation.
 * <p>
 * {@code tolerance} n'est jamais implicite : sur une metrique continue,
 * {@code EQ}/{@code NEQ} exigent une tolerance strictement positive.
 * La comparaison devient {@code |actual - threshold| <= tolerance}.
 *
 * @param operator  operateur de comparaison
 * @param threshold seuil
 * @param tolerance tolerance absolue (0 = comparaison exacte, deconseillee sur flottants)
 * @param severity  severite en cas d'echec
 */
public record Expectation(AssertionOperator operator, double threshold, double tolerance,
                          Severity severity) {

    public Expectation {
        Objects.requireNonNull(operator, "operator required");
        Objects.requireNonNull(severity, "severity required");
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("threshold must be finite");
        }
        if (!Double.isFinite(tolerance) || tolerance < 0) {
            throw new IllegalArgumentException("tolerance must be a non-negative finite number");
        }
    }

    public static Expectation atMost(double threshold, Severity severity) {
        return new Expectation(AssertionOperator.LTE, threshold, 0, severity);
    }

    public static Expectation atLeast(double threshold, Severity severity) {
        return new Expectation(AssertionOperator.GTE, threshold, 0, severity);
    }

    public static Expectation equals(double threshold, double tolerance, Severity severity) {
        return new Expectation(AssertionOperator.EQ, threshold, tolerance, severity);
    }

    /** @return vrai si l'attente est satisfaite par la valeur observee. */
    public boolean isSatisfiedBy(double actual) {
        if (operator == AssertionOperator.EQ || operator == AssertionOperator.NEQ) {
            boolean near = Math.abs(actual - threshold) <= tolerance;
            return operator == AssertionOperator.EQ ? near : !near;
        }
        return operator.evaluate(actual, threshold);
    }

    /** @return une representation courte : {@code "p95 <= 500 ms"}. */
    public String describe(String unit) {
        String suffix = unit == null || unit.isBlank() ? "" : " " + unit;
        String symbol = switch (operator) {
            case LT -> "<";
            case LTE -> "<=";
            case GT -> ">";
            case GTE -> ">=";
            case EQ -> tolerance > 0 ? "==" : "==";
            case NEQ -> "!=";
        };
        String toleranceText = tolerance > 0 ? " ± " + tolerance + suffix : "";
        return symbol + " " + threshold + suffix + toleranceText;
    }
}

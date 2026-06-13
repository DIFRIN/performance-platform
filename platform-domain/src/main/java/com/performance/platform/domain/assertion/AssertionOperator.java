package com.performance.platform.domain.assertion;

/**
 * Opérateur de comparaison pour les assertions de performance.
 */
public enum AssertionOperator {
    LT, LTE, GT, GTE, EQ, NEQ;

    public boolean evaluate(double actual, double expected) {
        return switch (this) {
            case LT  -> actual < expected;
            case LTE -> actual <= expected;
            case GT  -> actual > expected;
            case GTE -> actual >= expected;
            case EQ  -> actual == expected;
            case NEQ -> actual != expected;
        };
    }
}

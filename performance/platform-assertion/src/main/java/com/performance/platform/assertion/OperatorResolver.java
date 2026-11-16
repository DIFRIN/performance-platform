package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.AssertionOperator;

import java.util.Locale;

/**
 * Resolution d'un operateur de comparaison depuis sa forme YAML.
 * <p>
 * Accepte les noms ({@code LTE}) et les symboles ({@code <=}) afin que les
 * deux grammaires historiques continuent de fonctionner.
 */
public final class OperatorResolver {

    private OperatorResolver() {
    }

    /**
     * @param value nom ou symbole d'operateur
     * @return l'operateur resolu
     * @throws IllegalArgumentException si la valeur est inconnue
     */
    public static AssertionOperator resolve(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Operator is required. Supported: LT, LTE, GT, GTE, EQ, NEQ (or <, <=, >, >=, ==, !=)");
        }
        String normalized = value.strip();
        return switch (normalized) {
            case "<" -> AssertionOperator.LT;
            case "<=" -> AssertionOperator.LTE;
            case ">" -> AssertionOperator.GT;
            case ">=" -> AssertionOperator.GTE;
            case "==", "=" -> AssertionOperator.EQ;
            case "!=", "<>" -> AssertionOperator.NEQ;
            default -> byName(normalized);
        };
    }

    /** @return le symbole affichable d'un operateur. */
    public static String symbol(AssertionOperator operator) {
        return switch (operator) {
            case LT -> "<";
            case LTE -> "<=";
            case GT -> ">";
            case GTE -> ">=";
            case EQ -> "==";
            case NEQ -> "!=";
        };
    }

    private static AssertionOperator byName(String value) {
        try {
            return AssertionOperator.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported operator: '" + value
                            + "'. Supported: LT, LTE, GT, GTE, EQ, NEQ (or <, <=, >, >=, ==, !=)");
        }
    }
}

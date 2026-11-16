package com.performance.platform.domain.execution;

/**
 * Part d'un grant attribuee a un agent (ADR-067).
 * <p>
 * Invariants : {@code count >= 1}, {@code 0 <= index < count}, {@code 0 < fraction <= 1}.
 *
 * @param index    rang de l'agent parmi les agents accordes
 * @param count    nombre total d'agents accordes
 * @param fraction fraction du total attribuee a l'agent
 */
public record GrantShare(int index, int count, double fraction) {

    public GrantShare {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1: " + count);
        }
        if (index < 0 || index >= count) {
            throw new IllegalArgumentException("index must be in [0," + count + "): " + index);
        }
        if (!(fraction > 0.0 && fraction <= 1.0)) {
            throw new IllegalArgumentException("fraction must be in (0,1]: " + fraction);
        }
    }

    /** Part unique : (0, 1, 1.0). */
    public static GrantShare single() {
        return new GrantShare(0, 1, 1.0);
    }

    /** Part egale : fraction = 1/count. */
    public static GrantShare equal(int index, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1: " + count);
        }
        return new GrantShare(index, count, 1.0 / count);
    }
}

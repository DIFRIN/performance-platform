package com.performance.platform.domain.assertion;

import java.util.List;
import java.util.Objects;

/**
 * Regle de combinaison des checks d'une assertion.
 *
 * @param kind   ALL, ANY ou QUORUM
 * @param quorum seuil pour QUORUM (ignore sinon)
 */
public record Rollup(RollupKind kind, int quorum) {

    public enum RollupKind { ALL, ANY, QUORUM }

    public Rollup {
        Objects.requireNonNull(kind, "kind required");
        if (kind == RollupKind.QUORUM && quorum <= 0) {
            throw new IllegalArgumentException("quorum must be positive");
        }
    }

    public static Rollup all() {
        return new Rollup(RollupKind.ALL, 0);
    }

    public static Rollup any() {
        return new Rollup(RollupKind.ANY, 0);
    }

    public static Rollup quorum(int quorum) {
        return new Rollup(RollupKind.QUORUM, quorum);
    }

    /**
     * Combine les checks en un statut unique.
     * <p>
     * Un {@code ERROR} l'emporte ; sinon un echec : {@code ALL} exige tous les
     * checks applicables en succes, {@code ANY} en exige un, {@code QUORUM(n)}
     * en exige n. Un {@code INCONCLUSIVE} empeche un succes global : la mesure
     * manque, donc le resultat n'est pas etabli.
     */
    public AssertionStatus combine(List<Check> checks) {
        if (checks == null || checks.isEmpty()) {
            return AssertionStatus.INCONCLUSIVE;
        }
        long errors = checks.stream().filter(c -> c.status() == AssertionStatus.ERROR).count();
        if (errors > 0) {
            return AssertionStatus.ERROR;
        }
        List<Check> applicable = checks.stream()
                .filter(c -> c.status() != AssertionStatus.SKIPPED)
                .toList();
        if (applicable.isEmpty()) {
            return AssertionStatus.SKIPPED;
        }
        long passed = applicable.stream().filter(c -> c.status() == AssertionStatus.PASSED).count();
        long failed = applicable.stream().filter(c -> c.status() == AssertionStatus.FAILED).count();
        long inconclusive = applicable.stream()
                .filter(c -> c.status() == AssertionStatus.INCONCLUSIVE).count();

        return switch (kind) {
            case ANY -> passed > 0 ? AssertionStatus.PASSED
                    : inconclusive > 0 ? AssertionStatus.INCONCLUSIVE
                    : AssertionStatus.FAILED;
            case QUORUM -> passed >= quorum ? AssertionStatus.PASSED
                    : failed > 0 ? AssertionStatus.FAILED
                    : AssertionStatus.INCONCLUSIVE;
            case ALL -> failed > 0 ? AssertionStatus.FAILED
                    : inconclusive > 0 ? AssertionStatus.INCONCLUSIVE
                    : AssertionStatus.PASSED;
        };
    }
}

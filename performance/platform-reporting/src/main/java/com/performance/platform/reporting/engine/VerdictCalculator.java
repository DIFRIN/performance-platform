package com.performance.platform.reporting.engine;

import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.Severity;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.reporting.model.AssertionReportEntry;

import java.util.List;

/**
 * Calcule le verdict global d'une campagne a partir des assertions.
 * <p>
 * Regles :
 * <ul>
 *   <li>{@link Verdict#SUCCESS} — toutes les assertions sont PASSED, SKIPPED
 *       ou INCONCLUSIVE (une mesure manquante est une limitation, pas un echec)</li>
 *   <li>{@link Verdict#WARNING} — au moins une assertion est FAILED</li>
 *   <li>{@link Verdict#FAILED} — au moins une assertion est ERROR</li>
 * </ul>
 * ERROR est prioritaire sur FAILED, qui est prioritaire sur SUCCESS.
 * <p>
 * Un controle d'integrite CRITICAL en echec (hors saturation, severite WARNING) rend le
 * verdict {@link Verdict#INVALID}, prioritaire sur tout autre verdict (ADR-069) :
 * INVALID &gt; FAILED &gt; WARNING &gt; SUCCESS.
 */
public final class VerdictCalculator {

    private VerdictCalculator() {
    }

    /**
     * @param entries les entrees d'assertion du rapport (peut etre vide)
     * @return le verdict global — SUCCESS si aucune entree
     */
    public static Verdict calculate(List<AssertionReportEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Verdict.SUCCESS;
        }
        boolean hasError = false;
        boolean hasFailed = false;
        for (AssertionReportEntry entry : entries) {
            AssertionStatus status = entry.verdict();
            if (status == AssertionStatus.ERROR) {
                hasError = true;
            } else if (status == AssertionStatus.FAILED) {
                hasFailed = true;
            }
        }
        if (hasError) {
            return Verdict.FAILED;
        }
        return hasFailed ? Verdict.WARNING : Verdict.SUCCESS;
    }

    /**
     * @param entries         les entrees d'assertion du rapport
     * @param integrityChecks les controles d'integrite des etapes d'injection (peut etre vide)
     * @return INVALID si un controle d'integrite critique a echoue, sinon le verdict des assertions
     */
    public static Verdict calculate(List<AssertionReportEntry> entries, List<Check> integrityChecks) {
        return applyIntegrity(calculate(entries), integrityChecks);
    }

    /** Applique la priorite INVALID a un verdict deja calcule. */
    public static Verdict applyIntegrity(Verdict base, List<Check> integrityChecks) {
        return isInvalid(integrityChecks) ? Verdict.INVALID : base;
    }

    /** @return vrai si un controle d'integrite critique est FAILED (la saturation seule ne compte pas). */
    public static boolean isInvalid(List<Check> integrityChecks) {
        return integrityChecks != null && integrityChecks.stream()
                .anyMatch(c -> c.status() == AssertionStatus.FAILED
                        && c.expected() != null && c.expected().severity() == Severity.CRITICAL);
    }
}

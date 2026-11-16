package com.performance.platform.reporting.engine;

import com.performance.platform.domain.assertion.AssertionStatus;
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
}

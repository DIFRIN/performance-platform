package com.performance.platform.reporting.engine;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.reporting.model.AssertionReportEntry;

import java.util.List;

/**
 * Calcule le verdict global d'une campagne à partir des résultats d'assertion.
 * <p>
 * Règles :
 * <ul>
 *   <li>{@link Verdict#SUCCESS} — toutes les assertions sont PASSED ou SKIPPED</li>
 *   <li>{@link Verdict#WARNING} — au moins une assertion est FAILED</li>
 *   <li>{@link Verdict#FAILED} — au moins une assertion est ERROR</li>
 * </ul>
 * ERROR est prioritaire sur FAILED, qui est prioritaire sur SUCCESS.
 */
public final class VerdictCalculator {

    private VerdictCalculator() {
        // Classe utilitaire
    }

    /**
     * Calcule le verdict à partir des entrées d'assertion.
     *
     * @param entries les entrées d'assertion du rapport (peut être vide)
     * @return le verdict global — SUCCESS si aucune entrée
     */
    public static Verdict calculate(List<AssertionReportEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Verdict.SUCCESS;
        }

        boolean hasError = false;
        boolean hasFailed = false;

        for (AssertionReportEntry entry : entries) {
            AssertionResult result = entry.result();
            if (result == null) continue;
            AssertionStatus status = result.status();
            if (status == AssertionStatus.ERROR) {
                hasError = true;
            } else if (status == AssertionStatus.FAILED) {
                hasFailed = true;
            }
        }

        if (hasError) return Verdict.FAILED;
        if (hasFailed) return Verdict.WARNING;
        return Verdict.SUCCESS;
    }
}

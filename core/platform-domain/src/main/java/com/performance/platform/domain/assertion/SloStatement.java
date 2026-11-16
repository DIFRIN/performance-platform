package com.performance.platform.domain.assertion;

/**
 * Intention textuelle d'une assertion (SLI/SLO).
 * <p>
 * Documentaire par contrat : ce texte n'est jamais converti en seuil.
 * Il est recopie dans {@link AssertionSummary} puis dans le rapport pour
 * expliquer ce que l'assertion pretend garantir.
 *
 * @param title     libelle court
 * @param sli       ce qui est mesure, en langage metier
 * @param slo       l'objectif contractuel
 * @param rationale justification du seuil
 */
public record SloStatement(String title, String sli, String slo, String rationale) {

    public SloStatement {
        title = title == null ? "" : title;
        sli = sli == null ? "" : sli;
        slo = slo == null ? "" : slo;
        rationale = rationale == null ? "" : rationale;
    }

    public static SloStatement empty() {
        return new SloStatement("", "", "", "");
    }

    /** @return vrai si aucune information textuelle n'est fournie. */
    public boolean blank() {
        return title.isBlank() && sli.isBlank() && slo.isBlank() && rationale.isBlank();
    }
}

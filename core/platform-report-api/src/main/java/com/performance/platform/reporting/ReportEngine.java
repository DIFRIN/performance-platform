package com.performance.platform.reporting;

import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.reporting.model.CampaignReport;

/**
 * Port principal du moteur de reporting.
 * Génère un {@link CampaignReport} à partir d'un état d'exécution complet.
 */
public interface ReportEngine {

    /**
     * Génère le rapport de campagne consolidé depuis l'état d'exécution.
     *
     * @param state l'état complet de l'exécution (statuts, contexte, phases)
     * @return le CampaignReport agrégé avec verdict, summaries et entrées par tâche
     * @throws ReportGenerationException si la génération échoue
     */
    CampaignReport generate(ExecutionState state) throws ReportGenerationException;
}

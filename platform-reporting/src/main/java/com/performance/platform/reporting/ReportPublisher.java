package com.performance.platform.reporting;

import com.performance.platform.reporting.model.CampaignReport;
import com.performance.platform.reporting.model.PublisherConfig;

/**
 * ⚡ Interface publique critique — toute modification = ADR obligatoire.
 * <p>
 * Contrat de publication d'un rapport de campagne vers une destination externe.
 * Chaque implémentation cible une {@link PublicationTarget} spécifique
 * (Confluence, S3, Git, etc.).
 */
public interface ReportPublisher {

    /**
     * Publie le rapport vers la destination configurée.
     *
     * @param report le rapport de campagne à publier
     * @param config la configuration de publication (cible, propriétés)
     * @throws PublicationException si la publication échoue
     */
    void publish(CampaignReport report, PublisherConfig config) throws PublicationException;

    /**
     * @return la cible de publication supportée par ce publisher
     */
    PublicationTarget getTarget();
}

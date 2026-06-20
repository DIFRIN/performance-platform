package com.performance.platform.reporting;

import com.performance.platform.domain.report.ReportFormat;
import com.performance.platform.reporting.model.CampaignReport;

/**
 * Contrat de rendu d'un rapport dans un format spécifique.
 * Chaque implémentation produit un format (HTML, PDF, JSON).
 */
public interface ReportRenderer {

    /**
     * Render le CampaignReport dans le format supporté.
     *
     * @param report le rapport de campagne à sérialiser
     * @return le contenu rendu sous forme d'octets
     * @throws RenderException si le rendu échoue
     */
    byte[] render(CampaignReport report) throws RenderException;

    /**
     * @return le format de sortie produit par ce renderer
     */
    ReportFormat getFormat();
}

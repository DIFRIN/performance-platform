package com.performance.platform.application.ports.out;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;

/**
 * Port sortant vers les systemes de publication de rapports.
 * Le cas d'usage {@code GenerateReportUseCase} delegue la publication a ce port.
 * 0 annotation framework.
 */
public interface ReportPublisherPort {

    /**
     * Publie un rapport genere vers sa destination.
     *
     * @param reportId    l'identifiant du rapport a publier
     * @param executionId l'identifiant de l'execution associee
     */
    void publish(ReportId reportId, ExecutionId executionId);
}

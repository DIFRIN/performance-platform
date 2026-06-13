package com.performance.platform.application.ports.in;

import com.performance.platform.application.exception.ReportGenerationException;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;

/**
 * Use case : generer un rapport pour une execution donnee.
 */
public interface GenerateReportUseCase {

    ReportId generate(ExecutionId id) throws ReportGenerationException;
}

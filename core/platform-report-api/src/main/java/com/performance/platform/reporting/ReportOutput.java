package com.performance.platform.reporting;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.reporting.model.CampaignReport;

import java.nio.file.Path;

/**
 * Port for publishing rendered campaign reports.
 */
public interface ReportOutput {
    Path write(ExecutionId executionId, CampaignReport report);
}

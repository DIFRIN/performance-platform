package com.performance.platform.app.cli;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.infrastructure.scheduling.SpringScheduleCalculator;
import com.performance.platform.reporting.output.ReportProperties;
import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.ExecutionProgressCalculator;
import org.springframework.core.io.ResourceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;

class CliInvalidVerdictTest {

    @TempDir Path dir;

    private CliScenarioRunner runner() {
        return new CliScenarioRunner(mock(ScenarioParsingUseCase.class), mock(ExecuteScenarioUseCase.class),
                mock(GetExecutionStatusUseCase.class), mock(ExecutionRepository.class),
                mock(ExecutionProgressCalculator.class), mock(ResourceLoader.class),
                new SpringScheduleCalculator(), new ReportProperties(dir.toString(), List.of()));
    }

    private void writeReport(String id, String json) throws Exception {
        Files.createDirectories(dir.resolve(id));
        Files.writeString(dir.resolve(id, "campaign.json"), json);
    }

    @Test
    void readsTopLevelVerdictNotNestedAssertionVerdict() throws Exception {
        writeReport("e1", "{\"assertionResults\":[{\"verdict\":\"PASSED\"}],"
                + "\"verdict\":\"INVALID\",\"verdictReason\":\"x\"}");
        assertThat(runner().readVerdict(ExecutionId.of("e1"))).isEqualTo(Verdict.INVALID);
    }

    @Test
    void successVerdictIsNotInvalid() throws Exception {
        writeReport("e2", "{\"verdict\":\"SUCCESS\",\"verdictReason\":\"x\"}");
        assertThat(runner().readVerdict(ExecutionId.of("e2"))).isEqualTo(Verdict.SUCCESS);
    }

    @Test
    void invalidExitCodeIsTwo() {
        assertThat(CliScenarioRunner.EXIT_INVALID).isEqualTo(2);
    }
}

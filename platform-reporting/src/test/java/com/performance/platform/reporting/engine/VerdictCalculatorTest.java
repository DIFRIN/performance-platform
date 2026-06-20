package com.performance.platform.reporting.engine;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.reporting.model.AssertionReportEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("VerdictCalculator")
class VerdictCalculatorTest {

    private static final TaskId taskId = new TaskId("assert-1");
    private static final Instant now = Instant.now();
    private static final Duration duration = Duration.ofMillis(10);

    @Nested
    @DisplayName("SUCCESS")
    class SuccessTests {

        @Test
        @DisplayName("when all assertions are PASSED")
        void whenAllPassed() {
            var entries = List.of(
                    entry(AssertionStatus.PASSED),
                    entry(AssertionStatus.PASSED)
            );
            assertEquals(Verdict.SUCCESS, VerdictCalculator.calculate(entries));
        }

        @Test
        @DisplayName("when all assertions are PASSED or SKIPPED")
        void whenPassedOrSkipped() {
            var entries = List.of(
                    entry(AssertionStatus.PASSED),
                    entry(AssertionStatus.SKIPPED),
                    entry(AssertionStatus.PASSED)
            );
            assertEquals(Verdict.SUCCESS, VerdictCalculator.calculate(entries));
        }

        @Test
        @DisplayName("when entries list is empty")
        void whenEntriesEmpty() {
            assertEquals(Verdict.SUCCESS, VerdictCalculator.calculate(List.of()));
        }

        @Test
        @DisplayName("when entries is null")
        void whenEntriesNull() {
            assertEquals(Verdict.SUCCESS, VerdictCalculator.calculate(null));
        }
    }

    @Nested
    @DisplayName("WARNING")
    class WarningTests {

        @Test
        @DisplayName("when at least one assertion is FAILED")
        void whenOneFailed() {
            var entries = List.of(
                    entry(AssertionStatus.PASSED),
                    entry(AssertionStatus.FAILED),
                    entry(AssertionStatus.PASSED)
            );
            assertEquals(Verdict.WARNING, VerdictCalculator.calculate(entries));
        }

        @Test
        @DisplayName("when all assertions are FAILED")
        void whenAllFailed() {
            var entries = List.of(
                    entry(AssertionStatus.FAILED),
                    entry(AssertionStatus.FAILED)
            );
            assertEquals(Verdict.WARNING, VerdictCalculator.calculate(entries));
        }

        @Test
        @DisplayName("when FAILED mixed with SKIPPED")
        void whenFailedAndSkipped() {
            var entries = List.of(
                    entry(AssertionStatus.FAILED),
                    entry(AssertionStatus.SKIPPED)
            );
            assertEquals(Verdict.WARNING, VerdictCalculator.calculate(entries));
        }
    }

    @Nested
    @DisplayName("FAILED")
    class FailedTests {

        @Test
        @DisplayName("when at least one assertion is ERROR")
        void whenOneError() {
            var entries = List.of(
                    entry(AssertionStatus.PASSED),
                    entry(AssertionStatus.ERROR),
                    entry(AssertionStatus.PASSED)
            );
            assertEquals(Verdict.FAILED, VerdictCalculator.calculate(entries));
        }

        @Test
        @DisplayName("ERROR has priority over FAILED")
        void whenErrorAndFailed() {
            var entries = List.of(
                    entry(AssertionStatus.FAILED),
                    entry(AssertionStatus.ERROR),
                    entry(AssertionStatus.FAILED)
            );
            assertEquals(Verdict.FAILED, VerdictCalculator.calculate(entries));
        }

        @Test
        @DisplayName("when all assertions are ERROR")
        void whenAllErrors() {
            var entries = List.of(
                    entry(AssertionStatus.ERROR),
                    entry(AssertionStatus.ERROR)
            );
            assertEquals(Verdict.FAILED, VerdictCalculator.calculate(entries));
        }
    }

    // ── helper ──
    private static AssertionReportEntry entry(AssertionStatus status) {
        var result = new AssertionResult(
                taskId, status, "assertion " + status,
                null, duration, now);
        return new AssertionReportEntry(taskId, result, null);
    }
}

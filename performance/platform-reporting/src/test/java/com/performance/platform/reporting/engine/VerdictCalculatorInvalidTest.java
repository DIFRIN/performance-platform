package com.performance.platform.reporting.engine;

import com.performance.platform.assertion.IntegrityCheckFactory;
import com.performance.platform.assertion.IntegrityInput;
import com.performance.platform.domain.assertion.AssertionFamily;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.IntegrityTolerances;
import com.performance.platform.domain.assertion.Rollup;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.reporting.model.AssertionReportEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("VerdictCalculator — INVALID (ADR-069)")
class VerdictCalculatorInvalidTest {

    private static final IntegrityTolerances TOL = IntegrityTolerances.defaults();

    private static List<Check> checks(double deviation, int granted, int reporting, boolean complete, double cpu) {
        return IntegrityCheckFactory.checks(
                new IntegrityInput(deviation, true, granted, reporting, complete, cpu), TOL);
    }

    @Test
    @DisplayName("sous-livraison + assertions vertes => INVALID")
    void underDeliveredWithGreenAssertions() {
        var verdict = VerdictCalculator.calculate(List.of(entry(AssertionStatus.PASSED)),
                checks(40.0, 2, 2, true, 10.0));
        assertEquals(Verdict.INVALID, verdict);
    }

    @Test
    @DisplayName("INVALID prioritaire sur FAILED")
    void invalidBeatsFailed() {
        assertEquals(Verdict.INVALID, VerdictCalculator.calculate(
                List.of(entry(AssertionStatus.ERROR)), checks(1.0, 2, 1, true, 10.0)));
    }

    @Test
    @DisplayName("agent perdu ou arret anticipe => INVALID")
    void lostAgentOrIncomplete() {
        assertTrue(VerdictCalculator.isInvalid(checks(1.0, 3, 2, true, 10.0)));
        assertTrue(VerdictCalculator.isInvalid(checks(1.0, 2, 2, false, 10.0)));
    }

    @Test
    @DisplayName("run nominal => verdict inchange")
    void nominalUnchanged() {
        var nominal = checks(1.0, 2, 2, true, 10.0);
        assertEquals(Verdict.SUCCESS, VerdictCalculator.calculate(List.of(entry(AssertionStatus.PASSED)), nominal));
        assertEquals(Verdict.WARNING, VerdictCalculator.calculate(List.of(entry(AssertionStatus.FAILED)), nominal));
        assertEquals(Verdict.FAILED, VerdictCalculator.calculate(List.of(entry(AssertionStatus.ERROR)), nominal));
    }

    @Test
    @DisplayName("saturation seule => pas INVALID")
    void saturationAloneIsNotInvalid() {
        var saturated = checks(1.0, 2, 2, true, 99.0);
        assertFalse(VerdictCalculator.isInvalid(saturated));
        assertEquals(Verdict.SUCCESS, VerdictCalculator.calculate(List.of(entry(AssertionStatus.PASSED)), saturated));
    }

    @Test
    @DisplayName("simulation opaque (INCONCLUSIVE) => pas INVALID")
    void opaqueSimulationIsNotInvalid() {
        var opaque = IntegrityCheckFactory.checks(
                new IntegrityInput(Double.NaN, false, 1, 1, true, 10.0), TOL);
        assertFalse(VerdictCalculator.isInvalid(opaque));
    }

    @Test
    @DisplayName("sans controle => verdict des assertions")
    void noIntegrityChecks() {
        assertEquals(Verdict.SUCCESS, VerdictCalculator.calculate(List.of(entry(AssertionStatus.PASSED)), List.of()));
        assertEquals(Verdict.SUCCESS, VerdictCalculator.calculate(List.of(), null));
    }

    @Test
    @DisplayName("tolerances lues depuis step.parameters.integrity")
    void tolerancesFromStepParameters() {
        var tol = IntegrityChecks.tolerances(Map.of("integrity",
                Map.of("maxArrivalDeviation", "10%", "maxGeneratorCpu", 80)));
        assertEquals(new IntegrityTolerances(10.0, 80.0), tol);
        assertEquals(TOL, IntegrityChecks.tolerances(Map.of()));
    }

    private static AssertionReportEntry entry(AssertionStatus status) {
        var id = new TaskId("assert-1");
        return new AssertionReportEntry(id, new AssertionSummary(id, status, "a", List.of(),
                Duration.ofMillis(10), Instant.now(), AssertionFamily.CUSTOM, null, List.of(), Rollup.all()));
    }
}

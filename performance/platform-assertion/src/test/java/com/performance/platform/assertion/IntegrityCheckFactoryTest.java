package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.IntegrityTolerances;
import com.performance.platform.domain.assertion.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntegrityCheckFactoryTest {

    private static final IntegrityTolerances TOL = IntegrityTolerances.defaults();

    private static Check by(List<Check> checks, String intent) {
        return checks.stream().filter(c -> c.intent().equals(intent)).findFirst().orElseThrow();
    }

    private static List<Check> run(double dev, boolean known, int granted, int reporting,
                                   boolean complete, double cpu) {
        return IntegrityCheckFactory.checks(
                new IntegrityInput(dev, known, granted, reporting, complete, cpu), TOL);
    }

    @Test
    void allPass() {
        List<Check> checks = run(1.0, true, 3, 3, true, 50.0);
        assertEquals(4, checks.size());
        checks.forEach(c -> assertEquals(AssertionStatus.PASSED, c.status()));
    }

    @Test
    void arrivalDeviationTruthTable() {
        assertEquals(AssertionStatus.PASSED, by(run(5.0, true, 1, 1, true, 0.1), "arrival-deviation").status());
        Check failed = by(run(5.1, true, 1, 1, true, 0.1), "arrival-deviation");
        assertEquals(AssertionStatus.FAILED, failed.status());
        assertEquals(5.0, failed.expected().threshold());
        assertEquals(Severity.CRITICAL, failed.expected().severity());
        assertEquals(5.1, failed.actualValue());
        assertEquals(AssertionStatus.INCONCLUSIVE,
                by(run(99.0, false, 1, 1, true, 0.1), "arrival-deviation").status());
        assertEquals(AssertionStatus.INCONCLUSIVE,
                by(run(Double.NaN, true, 1, 1, true, 0.1), "arrival-deviation").status());
    }

    @Test
    void agentLossTruthTable() {
        assertEquals(AssertionStatus.PASSED, by(run(0, true, 2, 2, true, 1), "agent-loss").status());
        Check failed = by(run(0, true, 3, 2, true, 1), "agent-loss");
        assertEquals(AssertionStatus.FAILED, failed.status());
        assertEquals(1.0, failed.actualValue());
    }

    @Test
    void incompleteRunTruthTable() {
        assertEquals(AssertionStatus.PASSED, by(run(0, true, 1, 1, true, 1), "incomplete-run").status());
        assertEquals(AssertionStatus.FAILED, by(run(0, true, 1, 1, false, 1), "incomplete-run").status());
    }

    @Test
    void generatorSaturationIsWarningOnly() {
        assertEquals(AssertionStatus.PASSED, by(run(0, true, 1, 1, true, 90.0), "generator-saturation").status());
        Check failed = by(run(0, true, 1, 1, true, 95.0), "generator-saturation");
        assertEquals(AssertionStatus.FAILED, failed.status());
        assertEquals(Severity.WARNING, failed.expected().severity());
        assertFalse(failed.isCriticalFailure(failed.expected().severity()));
        assertEquals(90.0, failed.expected().threshold());
    }

    @Test
    void customTolerancesApplied() {
        List<Check> checks = IntegrityCheckFactory.checks(
                new IntegrityInput(8.0, true, 1, 1, true, 10), new IntegrityTolerances(10.0, 50.0));
        assertEquals(AssertionStatus.PASSED, by(checks, "arrival-deviation").status());
        assertEquals(10.0, by(checks, "arrival-deviation").expected().threshold());
    }
}

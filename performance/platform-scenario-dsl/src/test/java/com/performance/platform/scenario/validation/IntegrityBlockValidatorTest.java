package com.performance.platform.scenario.validation;

import com.performance.platform.domain.scenario.Phase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrityBlockValidatorTest {

    private final IntegrityBlockValidator validator = new IntegrityBlockValidator();

    private List<ValidationError> check(Phase phase, Object block) {
        return validator.validate("load", phase, Map.of("integrity", block));
    }

    private static void assertRule(List<ValidationError> errors, String rule) {
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("step.integrity." + rule, errors.get(0).field());
        assertTrue(errors.get(0).message().contains("'load'"), errors.get(0).message());
    }

    @Test
    void validPercentAndNumberPass() {
        assertTrue(check(Phase.INJECTION, Map.of("maxArrivalDeviation", "5%", "maxGeneratorCpu", 90)).isEmpty());
        assertTrue(check(Phase.INJECTION, Map.of("maxGeneratorCpu", "100")).isEmpty());
    }

    @Test
    void noBlockPasses() {
        assertTrue(validator.validate("load", Phase.INJECTION, Map.of("x", 1)).isEmpty());
        assertTrue(validator.validate("load", Phase.PREPARATION, null).isEmpty());
    }

    @Test
    void outOfRangeRejected() {
        assertRule(check(Phase.INJECTION, Map.of("maxArrivalDeviation", "0%")), "maxArrivalDeviation");
        assertRule(check(Phase.INJECTION, Map.of("maxGeneratorCpu", 100.5)), "maxGeneratorCpu");
        assertRule(check(Phase.INJECTION, Map.of("maxGeneratorCpu", -3)), "maxGeneratorCpu");
    }

    @Test
    void malformedValueRejected() {
        assertRule(check(Phase.INJECTION, Map.of("maxArrivalDeviation", "abc")), "maxArrivalDeviation");
        assertRule(check(Phase.INJECTION, Map.of("maxArrivalDeviation", List.of(1))), "maxArrivalDeviation");
    }

    @Test
    void unknownKeyRejected() {
        assertRule(check(Phase.INJECTION, Map.of("maxFoo", 5)), "key");
    }

    @Test
    void nonInjectionPhaseRejected() {
        assertRule(check(Phase.ASSERTION, Map.of("maxGeneratorCpu", 90)), "phase");
    }

    @Test
    void nonMappingRejected() {
        assertRule(check(Phase.INJECTION, "5%"), "block");
    }
}

package com.performance.platform.scenario.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadPlanValidatorTest {

    private final WorkloadPlanValidator validator = new WorkloadPlanValidator();

    private static Map<String, Object> hold(boolean measured) {
        return Map.of("hold", Map.of("level", 10, "duration", "30s", "measured", measured));
    }

    private static Map<String, Object> plan(String model, Object... stages) {
        return Map.of("model", model, "stages", List.of(stages));
    }

    private List<ValidationError> check(Map<String, Object> workload) {
        return validator.validate("load", Map.of("workload", workload));
    }

    private static void assertRule(List<ValidationError> errors, String rule) {
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("step.workload." + rule, errors.get(0).field());
        assertTrue(errors.get(0).message().contains("'load'"), errors.get(0).message());
    }

    @Test
    void validPlanPasses() {
        assertTrue(check(plan("open", hold(false), hold(true))).isEmpty());
    }

    @Test
    void noWorkloadDeclaredPasses() {
        assertTrue(validator.validate("load", Map.of("simulation", "x")).isEmpty());
    }

    @Test
    void emptyPlanRejected() {
        assertRule(check(Map.of("model", "open", "stages", List.of())), "empty");
    }

    @Test
    void burstInOpenPasses() {
        assertTrue(check(plan("open", Map.of("burst", Map.of("users", 5)))).isEmpty());
    }

    @Test
    void burstInClosedRejected() {
        assertRule(check(plan("closed", Map.of("burst", Map.of("users", 5)))), "burst-closed");
    }

    @Test
    void measuredStagePasses() {
        assertTrue(check(plan("closed", hold(false), hold(true))).isEmpty());
    }

    @Test
    void noMeasuredStageRejected() {
        assertRule(check(plan("open", hold(false))), "measured");
    }

    @Test
    void knownStageKindPasses() {
        assertTrue(check(plan("open", Map.of("pause", Map.of("duration", "5s")), hold(true))).isEmpty());
    }

    @Test
    void unknownStageKindRejected() {
        assertRule(check(plan("open", Map.of("wave", Map.of("duration", "5s")))), "stage-kind");
    }

    @Test
    void withoutUnitPasses() {
        assertTrue(check(plan("open", hold(true))).isEmpty());
    }

    @Test
    void unitKeyRejected() {
        Map<String, Object> w = Map.of("model", "open", "unit", "requests_per_second",
            "stages", List.of(hold(true)));
        assertRule(check(w), "unit");
    }

    @Test
    void nonPositiveDurationRejected() {
        var errors = check(plan("open",
            Map.of("hold", Map.of("level", 1, "duration", "0s"))));
        assertRule(errors, "plan");
    }

    @Test
    void presetLoadModelExpandedAndValidated() {
        var ok = validator.validate("load", Map.of("loadModel",
            Map.of("type", "RAMP", "parameters", Map.of("from", 1, "to", 10, "durationSeconds", 60))));
        assertTrue(ok.isEmpty(), ok.toString());
        assertRule(validator.validate("load", Map.of("loadModel", Map.of("type", "NOPE"))), "loadModel");
    }

    @Test
    void workloadAndLoadModelExclusive() {
        var errors = validator.validate("load", Map.of("workload", plan("open", hold(true)),
            "loadModel", Map.of("type", "RAMP")));
        assertRule(errors, "exclusive");
    }
}

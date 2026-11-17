package com.performance.platform.scenario.parser;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.domain.scenario.StepDefinition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Workload parsing (ADR-064)")
class WorkloadParsingTest {

    private final YamlScenarioParser parser = new YamlScenarioParser();

    private static String yaml(String params) {
        return """
                scenario:
                  id: wl
                  name: WL
                  version: 1.0.0
                  steps:
                    - id: inject
                      task: http-request
                      phase: INJECTION
                      parameters:
                """ + params.indent(8);
    }

    private StepDefinition step(String params) {
        return parser.parse(yaml(params)).steps().get(0);
    }

    @Test
    @DisplayName("preset loadModel is expanded to canonical workload")
    void preset() {
        var p = step("""
                loadModel:
                  type: RAMP
                  parameters: { from: 1, to: 10, durationSeconds: 30 }
                """).parameters();
        assertFalse(p.containsKey("loadModel"));
        assertTrue(p.get("workload") instanceof Map<?, ?>);
        assertEquals("open", ((Map<?, ?>) p.get("workload")).get("model"));
    }

    @Test
    @DisplayName("explicit workload is normalized")
    void workload() {
        var p = step("""
                workload:
                  model: open
                  stages:
                    - { hold: { level: 5, duration: 10s } }
                """).parameters();
        assertTrue(p.get("workload") instanceof Map<?, ?>);
    }

    @Test
    @DisplayName("both keys are rejected")
    void both() {
        var e = assertThrows(ScenarioParsingException.class, () -> step("""
                loadModel: { type: CONSTANT, parameters: { usersPerSec: 1, durationSeconds: 5 } }
                workload: { model: open, stages: [] }
                """));
        assertTrue(e.getErrors().toString().contains("mutually exclusive"), e.getErrors().toString());
    }

    @Test
    @DisplayName("invalid preset reports the step")
    void invalid() {
        var e = assertThrows(ScenarioParsingException.class, () -> step("""
                loadModel: { type: NOPE }
                """));
        assertTrue(e.getErrors().toString().contains("steps[0]"), e.getErrors().toString());
    }
}

package com.performance.platform.scenario.parser;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.domain.scenario.AgentLossPolicy;
import com.performance.platform.domain.scenario.StepDefinition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("minAgents / onAgentLoss parsing (ADR-067)")
class AgentLossParsingTest {

    private final YamlScenarioParser parser = new YamlScenarioParser();

    private StepDefinition step(String extra) {
        return parser.parse("""
                scenario:
                  id: al
                  name: AL
                  version: 1.0.0
                  steps:
                    - id: inject
                      task: http-request
                      phase: INJECTION
                """ + extra.indent(6)).steps().get(0);
    }

    @Test
    @DisplayName("defaults when keys absent")
    void defaults() {
        var s = step("");
        assertEquals(1, s.minAgents());
        assertEquals(AgentLossPolicy.ABORT, s.onAgentLoss());
    }

    @Test
    @DisplayName("explicit values are read")
    void explicit() {
        var s = step("minAgents: 3\nonAgentLoss: continue\n");
        assertEquals(3, s.minAgents());
        assertEquals(AgentLossPolicy.CONTINUE, s.onAgentLoss());
    }

    @Test
    @DisplayName("unknown onAgentLoss reports step id")
    void invalidLoss() {
        var e = assertThrows(ScenarioParsingException.class, () -> step("onAgentLoss: retry\n"));
        assertTrue(String.join(";", e.getErrors()).contains("inject") && String.join(";", e.getErrors()).contains("onAgentLoss"));
    }

    @Test
    @DisplayName("minAgents < 1 reports step id")
    void invalidMin() {
        var e = assertThrows(ScenarioParsingException.class, () -> step("minAgents: 0\n"));
        assertTrue(String.join(";", e.getErrors()).contains("inject") && String.join(";", e.getErrors()).contains("minAgents"));
    }
}

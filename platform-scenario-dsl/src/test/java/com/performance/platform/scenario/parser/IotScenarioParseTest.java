package com.performance.platform.scenario.parser;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IoT Scenario Parse Verification")
class IotScenarioParseTest {

    private static final String BASE = "/mnt/c/Dev/wsl-shared/performance-platform/platform-deployment/examples/scenarios/";

    private final YamlScenarioParser parser = new YamlScenarioParser();

    @Test
    @DisplayName("iot-dispatcher-local.yaml is parseable")
    void parseLocalScenario() throws Exception {
        var file = Path.of(BASE, "iot-dispatcher-local.yaml");
        ScenarioDefinition scenario = parser.parseFile(file);

        assertEquals("iot-dispatcher-local", scenario.id().value());
        assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
        assertEquals(6, scenario.steps().size());
        assertEquals("reset-wiremock", scenario.steps().get(0).id().value());
        assertEquals("http-client", scenario.steps().get(0).taskName());
    }

    @Test
    @DisplayName("iot-dispatcher-distributed.yaml is parseable")
    void parseDistributedScenario() throws Exception {
        var file = Path.of(BASE, "iot-dispatcher-distributed.yaml");
        try {
            ScenarioDefinition scenario = parser.parseFile(file);
            assertEquals("iot-dispatcher-distributed", scenario.id().value());
            assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
            assertEquals(5, scenario.steps().size());
            assertEquals("reset-wiremock", scenario.steps().get(0).id().value());
        } catch (ScenarioParsingException e) {
            // Print the actual parsing errors for debugging
            System.err.println("PARSE ERRORS: " + e.getMessage() + " — errors: " + e.getErrors());
            throw e;
        }
    }

    @Test
    @DisplayName("device-api-local.yaml is parseable")
    void parseDeviceApiLocalScenario() throws Exception {
        var file = Path.of(BASE, "device-api-local.yaml");
        ScenarioDefinition scenario = parser.parseFile(file);

        assertEquals("device-api-local", scenario.id().value());
        assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
        assertEquals(7, scenario.steps().size());
        assertEquals("reset-devices", scenario.steps().get(0).id().value());
        assertEquals("database", scenario.steps().get(0).taskName());
        assertEquals("load-test", scenario.steps().get(4).id().value());
        assertEquals("gatling", scenario.steps().get(4).taskName());
        assertEquals("assert-db-intact", scenario.steps().get(6).id().value());
        assertTrue(scenario.loadModels().containsKey("device-api-ramp"));
    }

    @Test
    @DisplayName("device-api-distributed.yaml is parseable")
    void parseDeviceApiDistributedScenario() throws Exception {
        var file = Path.of(BASE, "device-api-distributed.yaml");
        try {
            ScenarioDefinition scenario = parser.parseFile(file);
            assertEquals("device-api-distributed", scenario.id().value());
            assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
            assertEquals(6, scenario.steps().size());
            assertEquals("reset-devices", scenario.steps().get(0).id().value());
            assertEquals("assert-db-intact", scenario.steps().get(5).id().value());
            assertTrue(scenario.loadModels().containsKey("device-api-high-ramp"));
        } catch (ScenarioParsingException e) {
            System.err.println("PARSE ERRORS: " + e.getMessage() + " — errors: " + e.getErrors());
            throw e;
        }
    }

    @Test
    @DisplayName("http-api-mock-agent-local.yaml is parseable — WireMock as Agent (LOCAL)")
    void parseHttpApiMockAgentLocalScenario() throws Exception {
        var file = Path.of(BASE, "http-api-mock-agent-local.yaml");
        ScenarioDefinition scenario = parser.parseFile(file);

        assertEquals("http-api-mock-agent-local", scenario.id().value());
        assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
        assertEquals(7, scenario.steps().size());

        // Verify step 1: spawn-mock (PREPARATION)
        assertEquals("spawn-mock", scenario.steps().get(0).id().value());
        assertEquals("mock-server", scenario.steps().get(0).taskName());
        assertEquals(Phase.PREPARATION, scenario.steps().get(0).phase());

        // Verify step 2: load-test (INJECTION) — dependsOn spawn-mock
        assertEquals("load-test", scenario.steps().get(1).id().value());
        assertEquals("gatling", scenario.steps().get(1).taskName());
        assertEquals(Phase.INJECTION, scenario.steps().get(1).phase());
        assertTrue(scenario.steps().get(1).dependsOn().stream()
                .anyMatch(d -> d.value().equals("spawn-mock")));

        // Verify step 6: assert-mock-hit (ASSERTION)
        assertEquals("assert-mock-hit", scenario.steps().get(5).id().value());
        assertEquals("http-mock", scenario.steps().get(5).taskName());
        assertEquals(Phase.ASSERTION, scenario.steps().get(5).phase());

        // Verify step 7: stop-mock (PREPARATION cleanup) — dependsOn all assertions
        assertEquals("stop-mock", scenario.steps().get(6).id().value());
        assertEquals("mock-server", scenario.steps().get(6).taskName());
        assertEquals(Phase.PREPARATION, scenario.steps().get(6).phase());
        assertEquals(4, scenario.steps().get(6).dependsOn().size());

        // Verify load model
        assertTrue(scenario.loadModels().containsKey("demo-ramp"));

        // Verify metadata
        assertEquals("perf-team", scenario.metadata().get("owner"));

        // Verify tags
        assertTrue(scenario.tags().contains("mock-agent"));
        assertTrue(scenario.tags().contains("http"));
    }

}

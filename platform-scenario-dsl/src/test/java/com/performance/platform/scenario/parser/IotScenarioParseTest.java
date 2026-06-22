package com.performance.platform.scenario.parser;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.domain.scenario.ExecutionMode;
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
        Path file = Path.of(BASE, "iot-dispatcher-local.yaml");
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
        Path file = Path.of(BASE, "iot-dispatcher-distributed.yaml");
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
        Path file = Path.of(BASE, "device-api-local.yaml");
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
        Path file = Path.of(BASE, "device-api-distributed.yaml");
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

}

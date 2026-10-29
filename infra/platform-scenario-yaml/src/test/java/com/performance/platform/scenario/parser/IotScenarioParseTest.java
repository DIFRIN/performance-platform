package com.performance.platform.scenario.parser;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.scenario.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IoT Scenario Parse Verification")
class IotScenarioParseTest {

    private static final Path SCENARIOS = TestPaths.exampleScenarios();

    private final YamlScenarioParser parser = new YamlScenarioParser();

    @Test
    @DisplayName("iot-dispatcher-local.yaml is parseable")
    void parseLocalScenario() throws Exception {
        var file = SCENARIOS.resolve("iot-dispatcher-local.yaml");
        ScenarioDefinition scenario = parser.parseFile(file);

        assertEquals("iot-dispatcher-local", scenario.id().value());
        assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
        assertEquals(7, scenario.steps().size());
        assertEquals("reset-wiremock", scenario.steps().get(0).id().value());
        assertEquals("http-client", scenario.steps().get(0).taskName());
        assertKafkaCountContract(scenario, "count-iot-commands",
                "assert-iot-commands-count", "inject-load", "iot-commands", 7000);
        assertEquals(1000, step(scenario, "preload-iot-commands")
                .parameters().get("messageCount"));
        assertEquals(Map.of(
                "type", "CONSTANT",
                "parameters", Map.of("usersPerSec", 200, "durationSeconds", 30)),
                step(scenario, "inject-load").parameters().get("loadModel"));
        assertEquals(Map.of(
                "target", "wiremock",
                "metric", "receivedCalls",
                "value", 7000,
                "operator", "GTE"),
                step(scenario, "assert-http-dispatched").parameters());
    }

    @Test
    @DisplayName("iot-dispatcher-distributed.yaml is parseable")
    void parseDistributedScenario() throws Exception {
        var file = SCENARIOS.resolve("iot-dispatcher-distributed.yaml");
        try {
            ScenarioDefinition scenario = parser.parseFile(file);
            assertEquals("iot-dispatcher-distributed", scenario.id().value());
            assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
            assertEquals(6, scenario.steps().size());
            assertEquals("reset-wiremock", scenario.steps().get(0).id().value());
            assertKafkaCountContract(scenario, "count-iot-commands",
                    "assert-iot-commands-count", "inject-load", "iot-commands", 10000);
            assertEquals(10000, step(scenario, "preload-iot-commands")
                    .parameters().get("messageCount"));
            assertEquals(Map.of(
                    "target", "wiremock",
                    "metric", "receivedCalls",
                    "value", 10000,
                    "operator", "GTE"),
                    step(scenario, "assert-http-dispatched").parameters());
        } catch (ScenarioParsingException e) {
            // Print the actual parsing errors for debugging
            System.err.println("PARSE ERRORS: " + e.getMessage() + " — errors: " + e.getErrors());
            throw e;
        }
    }

    @Test
    @DisplayName("device-api-local.yaml is parseable")
    void parseDeviceApiLocalScenario() throws Exception {
        var file = SCENARIOS.resolve("device-api-local.yaml");
        ScenarioDefinition scenario = parser.parseFile(file);

        assertEquals("device-api-local", scenario.id().value());
        assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
        assertEquals(8, scenario.steps().size());
        assertEquals("reset-devices", scenario.steps().get(0).id().value());
        assertEquals("database", scenario.steps().get(0).taskName());
        assertEquals("load-test", scenario.steps().get(4).id().value());
        assertEquals("gatling", scenario.steps().get(4).taskName());
        assertEquals("assert-db-intact", scenario.steps().get(7).id().value());
        assertTrue(scenario.loadModels().containsKey("device-api-ramp"));
        var warmup = scenario.steps().get(2).parameters();
        assertEquals(Map.of("device_id", "device-0001"), warmup.get("queryParams"));
        assertFalse(warmup.containsKey("body"));
        assertDeviceApiInjection(scenario, 30000);
    }

    @Test
    @DisplayName("device-api-distributed.yaml is parseable")
    void parseDeviceApiDistributedScenario() throws Exception {
        var file = SCENARIOS.resolve("device-api-distributed.yaml");
        try {
            ScenarioDefinition scenario = parser.parseFile(file);
            assertEquals("device-api-distributed", scenario.id().value());
            assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
            assertEquals(7, scenario.steps().size());
            assertEquals("reset-devices", scenario.steps().get(0).id().value());
            assertEquals("assert-db-intact", scenario.steps().get(6).id().value());
            assertTrue(scenario.loadModels().containsKey("device-api-high-ramp"));
            assertDeviceApiInjection(scenario, 600000);
        } catch (ScenarioParsingException e) {
            System.err.println("PARSE ERRORS: " + e.getMessage() + " — errors: " + e.getErrors());
            throw e;
        }
    }

    private static void assertDeviceApiInjection(ScenarioDefinition scenario, int expectedEvents) {
        var validation = new com.performance.platform.scenario.validation.DefaultScenarioValidator()
                .validate(scenario);
        assertTrue(validation.errors().stream().noneMatch(error -> error.field().startsWith("step.loadModel")),
                "Inline loadModel must pass validation: " + validation.errorMessages());
        var parameters = step(scenario, "load-test").parameters();
        assertEquals("POST", parameters.get("method"));
        assertEquals("submit-event", parameters.get("path"));
        assertEquals(Map.of("device_id", "device-{index:4}"), parameters.get("queryParams"));
        assertFalse(parameters.containsKey("body"));
        int targetRate = expectedEvents == 30000 ? 1000 : 10000;
        int durationSeconds = expectedEvents == 30000 ? 60 : 120;
        assertEquals(Map.of(
                "type", "RAMP",
                "parameters", Map.of(
                        "from", 1,
                        "to", targetRate,
                        "durationSeconds", durationSeconds)),
                parameters.get("loadModel"));

        assertKafkaCountContract(scenario, "count-device-events", "assert-kafka-produced",
                "load-test", "device-events", expectedEvents);
        assertDatabaseAssertionContract(scenario);
    }

    private static void assertKafkaCountContract(ScenarioDefinition scenario, String countId,
                                                 String assertionId, String workloadId,
                                                 String topic, int expectedCount) {
        var count = step(scenario, countId);
        assertEquals("kafka-consumer", count.taskName());
        assertEquals(Phase.ASSERTION, count.phase());
        assertEquals(Map.of("cluster", "iot-sut", "topic", topic, "operation", "COUNT"),
                count.parameters());
        assertTrue(count.dependsOn().stream().anyMatch(id -> id.value().equals(workloadId)));

        var assertion = step(scenario, assertionId);
        assertEquals("kafka", assertion.taskName());
        assertEquals(Phase.ASSERTION, assertion.phase());
        assertEquals(Map.of(
                "metric", "consumedCount",
                "refTaskId", countId,
                "operator", "GTE",
                "value", expectedCount),
                assertion.parameters());
        assertTrue(assertion.dependsOn().stream().anyMatch(id -> id.value().equals(countId)));
    }

    private static void assertDatabaseAssertionContract(ScenarioDefinition scenario) {
        var assertion = step(scenario, "assert-db-intact");
        assertEquals("database-assertion", assertion.taskName());
        assertEquals(Map.of(
                "datasource", "sut-db",
                "query", "SELECT COUNT(*) FROM devices WHERE is_active = true",
                "value", 10000,
                "operator", "EQ"),
                assertion.parameters());
    }

    private static com.performance.platform.domain.scenario.StepDefinition step(
            ScenarioDefinition scenario, String id) {
        return scenario.steps().stream()
                .filter(candidate -> candidate.id().value().equals(id))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("http-api-mock-agent-local.yaml is parseable — WireMock as Agent (LOCAL)")
    void parseHttpApiMockAgentLocalScenario() throws Exception {
        var file = SCENARIOS.resolve("http-api-mock-agent-local.yaml");
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

    @Test
    @DisplayName("http-api-mock-agent-distributed.yaml is parseable — WireMock as Agent (DISTRIBUTED)")
    void parseHttpApiMockAgentDistributedScenario() throws Exception {
        var file = SCENARIOS.resolve("http-api-mock-agent-distributed.yaml");
        try {
            ScenarioDefinition scenario = parser.parseFile(file);

            assertEquals("http-api-mock-agent-distributed", scenario.id().value());
            assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
            assertEquals(7, scenario.steps().size());

            // Verify step 1: spawn-mock (PREPARATION) — routing: task=mock-server -> mock-agent
            assertEquals("spawn-mock", scenario.steps().get(0).id().value());
            assertEquals("mock-server", scenario.steps().get(0).taskName());
            assertEquals(Phase.PREPARATION, scenario.steps().get(0).phase());

            // Verify step 2: load-test (INJECTION) — dependsOn spawn-mock
            assertEquals("load-test", scenario.steps().get(1).id().value());
            assertEquals("gatling", scenario.steps().get(1).taskName());
            assertEquals(Phase.INJECTION, scenario.steps().get(1).phase());
            assertTrue(scenario.steps().get(1).dependsOn().stream()
                    .anyMatch(d -> d.value().equals("spawn-mock")));

            // Verify step 6: assert-mock-hit (ASSERTION) — routing: task=http-mock -> standard-agent
            assertEquals("assert-mock-hit", scenario.steps().get(5).id().value());
            assertEquals("http-mock", scenario.steps().get(5).taskName());
            assertEquals(Phase.ASSERTION, scenario.steps().get(5).phase());
            assertEquals("mock-api", scenario.steps().get(5).parameters().get("target"));

            // Verify step 7: stop-mock (PREPARATION) — dependsOn all 4 assertions
            assertEquals("stop-mock", scenario.steps().get(6).id().value());
            assertEquals("mock-server", scenario.steps().get(6).taskName());
            assertEquals(Phase.PREPARATION, scenario.steps().get(6).phase());
            assertEquals(4, scenario.steps().get(6).dependsOn().size());

            // Verify load model — higher load than LOCAL
            assertTrue(scenario.loadModels().containsKey("demo-high-ramp"));

            // Verify metadata
            assertEquals("perf-team", scenario.metadata().get("owner"));

            // Verify tags (should NOT contain 'local')
            assertTrue(scenario.tags().contains("distributed"));
            assertTrue(scenario.tags().contains("mock-agent"));
            assertTrue(scenario.tags().contains("http"));

            // Verify no agentTags field — routing is by task name only (configuration-driven)
            // The parser ignores unknown fields, so this is verified by absence of agentTags in step parameters
            for (var step : scenario.steps()) {
                assertNull(step.parameters().get("agentTags"),
                        "Step " + step.id().value() + " should not have agentTags");
            }
        } catch (ScenarioParsingException e) {
            System.err.println("PARSE ERRORS: " + e.getMessage() + " — errors: " + e.getErrors());
            throw e;
        }
    }

}

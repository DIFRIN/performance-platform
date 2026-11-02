package com.performance.platform.scenario.parser;

import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.scenario.TestPaths;
import com.performance.platform.scenario.validation.DefaultScenarioValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Example scenarios parse and validate")
class ExampleScenarioParseTest {

    private static final Path SCENARIOS = TestPaths.exampleScenarios();

    private final YamlScenarioParser parser = new YamlScenarioParser();

    @Test
    @DisplayName("01-kafka-to-http-local.yaml")
    void parsesKafkaToHttpLocal() throws Exception {
        ScenarioDefinition scenario = parseValid("01-kafka-to-http-local.yaml");

        assertEquals("01-kafka-to-http-local", scenario.id().value());
        assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
        assertEquals(8, scenario.steps().size());

        StepDefinition startMock = step(scenario, "start-mock");
        assertEquals("mock-server", startMock.taskName());
        assertEquals(8090, startMock.parameters().get("port"));
        assertEquals("classpath:mock", startMock.parameters().get("mappingsPath"));

        StepDefinition inject = step(scenario, "inject-commands");
        assertEquals("kafka-producer", inject.taskName());
        assertEquals("iot-sut", inject.parameters().get("cluster"));
        assertEquals("iot-commands", inject.parameters().get("topic"));

        StepDefinition assertion = step(scenario, "assert-mock-called");
        assertEquals("http-mock", assertion.taskName());
        assertEquals("wiremock", assertion.parameters().get("target"));
        assertEquals("GTE", assertion.parameters().get("operator"));
    }

    @Test
    @DisplayName("01-kafka-to-http-distributed.yaml")
    void parsesKafkaToHttpDistributed() throws Exception {
        ScenarioDefinition scenario = parseValid("01-kafka-to-http-distributed.yaml");

        assertEquals("01-kafka-to-http-distributed", scenario.id().value());
        assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
        assertEquals(8, scenario.steps().size());
        assertEquals(5000, step(scenario, "inject-commands").parameters().get("messageCount"));
        assertEquals("wiremock", step(scenario, "assert-mock-called")
                .parameters().get("target"));
    }

    @Test
    @DisplayName("02-http-to-kafka-local.yaml")
    void parsesHttpToKafkaLocal() throws Exception {
        ScenarioDefinition scenario = parseValid("02-http-to-kafka-local.yaml");

        assertEquals("02-http-to-kafka-local", scenario.id().value());
        assertEquals(ExecutionMode.LOCAL, scenario.executionMode());
        assertEquals(7, scenario.steps().size());

        StepDefinition injection = step(scenario, "inject-events");
        assertEquals("gatling", injection.taskName());
        assertEquals("sut-api", injection.parameters().get("target"));

        StepDefinition count = step(scenario, "count-events");
        assertEquals("kafka-consumer", count.taskName());
        assertEquals("iot-sut", count.parameters().get("cluster"));
        assertEquals("device-events", count.parameters().get("topic"));

        StepDefinition assertion = step(scenario, "assert-events-produced");
        assertEquals("kafka", assertion.taskName());
        assertEquals("count-events", assertion.parameters().get("refTaskId"));
    }

    @Test
    @DisplayName("02-http-to-kafka-distributed.yaml")
    void parsesHttpToKafkaDistributed() throws Exception {
        ScenarioDefinition scenario = parseValid("02-http-to-kafka-distributed.yaml");

        assertEquals("02-http-to-kafka-distributed", scenario.id().value());
        assertEquals(ExecutionMode.DISTRIBUTED, scenario.executionMode());
        assertEquals(7, scenario.steps().size());
        assertEquals("sut-api", step(scenario, "inject-events").parameters().get("target"));
        assertEquals("device-events", step(scenario, "count-events")
                .parameters().get("topic"));
    }

    private ScenarioDefinition parseValid(String file) throws Exception {
        ScenarioDefinition scenario = parser.parseFile(SCENARIOS.resolve(file));
        var validation = new DefaultScenarioValidator().validate(scenario);
        assertTrue(validation.errors().isEmpty(),
                () -> file + " validation errors: " + validation.errors());
        return scenario;
    }

    private static StepDefinition step(ScenarioDefinition scenario, String id) {
        StepDefinition step = scenario.steps().stream()
                .filter(candidate -> candidate.id().value().equals(id))
                .findFirst()
                .orElse(null);
        assertNotNull(step, "missing step " + id);
        return step;
    }
}

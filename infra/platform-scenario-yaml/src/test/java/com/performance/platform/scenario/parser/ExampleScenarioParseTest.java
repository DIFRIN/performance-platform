package com.performance.platform.scenario.parser;

import com.performance.platform.domain.scenario.FanOut;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.ServiceScope;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.scenario.TestPaths;
import com.performance.platform.scenario.validation.DefaultScenarioValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les exemples sont des fichiers uniques valables dans les deux modes
 * d'execution (ADR-053) et utilisent les service steps (ADR-054).
 */
@DisplayName("Example scenarios parse and validate")
class ExampleScenarioParseTest {

    private static final Path SCENARIOS = TestPaths.exampleScenarios();

    private final YamlScenarioParser parser = new YamlScenarioParser();

    @Test
    @DisplayName("01-kafka-to-http.yaml")
    void parsesKafkaToHttp() throws Exception {
        ScenarioDefinition scenario = parseValid("01-kafka-to-http.yaml");

        assertEquals("01-kafka-to-http", scenario.id().value());
        assertEquals(6, scenario.steps().size());

        StepDefinition startMock = step(scenario, "start-mock");
        assertEquals("mock-server", startMock.taskName());
        assertEquals(ServiceScope.INJECTION, startMock.during());
        assertNull(startMock.phase());
        assertEquals(8090, startMock.parameters().get("port"));

        StepDefinition inject = step(scenario, "inject-commands");
        assertEquals("kafka-producer", inject.taskName());
        assertEquals(FanOut.ONE, inject.fanOut());
        assertEquals("iot-commands", inject.parameters().get("topic"));

        StepDefinition assertion = step(scenario, "assert-mock-called");
        assertEquals("direct-assertion", assertion.taskName());
        assertEquals("start-mock", assertion.ref().value());
        assertEquals("http.requests.matched", assertion.parameters().get("metric"));
    }

    @Test
    @DisplayName("02-http-to-kafka.yaml")
    void parsesHttpToKafka() throws Exception {
        ScenarioDefinition scenario = parseValid("02-http-to-kafka.yaml");

        assertEquals("02-http-to-kafka", scenario.id().value());
        assertEquals(6, scenario.steps().size());

        StepDefinition injection = step(scenario, "inject-events");
        assertEquals("gatling", injection.taskName());
        assertEquals(FanOut.ALL, injection.fanOut());
        assertEquals("sut-api", injection.parameters().get("target"));
        assertTrue(injection.parameters().get("loadModel") instanceof java.util.Map);

        StepDefinition assertion = step(scenario, "assert-events-produced");
        assertEquals("count-events", assertion.ref().value());
    }

    @Test
    @DisplayName("03-assertion-model.yaml")
    void parsesAssertionModel() throws Exception {
        ScenarioDefinition scenario = parseValid("03-assertion-model.yaml");

        assertEquals("03-assertion-model", scenario.id().value());
        assertNotNull(step(scenario, "marker-present"));
        assertNotNull(step(scenario, "api-slo"));
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

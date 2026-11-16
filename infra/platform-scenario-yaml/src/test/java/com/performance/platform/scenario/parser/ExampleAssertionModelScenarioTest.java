package com.performance.platform.scenario.parser;

import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.scenario.TestPaths;
import com.performance.platform.scenario.validation.DefaultScenarioValidator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Example scenario 03 - assertion model")
class ExampleAssertionModelScenarioTest {

    private final YamlScenarioParser parser = new YamlScenarioParser();

    @Test
    @DisplayName("parses and validates the assertion-model example")
    void parsesAssertionModelExample() throws Exception {
        Path file = TestPaths.exampleScenarios().resolve("03-assertion-model.yaml");
        ScenarioDefinition scenario = parser.parseFile(file);

        assertEquals("03-assertion-model", scenario.id().value());
        var validation = new DefaultScenarioValidator().validate(scenario);
        assertTrue(validation.errors().isEmpty(), () -> "errors: " + validation.errors());

        StepDefinition direct = step(scenario, "marker-present");
        assertEquals("direct-assertion", direct.taskName());
        assertTrue(direct.parameters().containsKey("description"));

        StepDefinition slo = step(scenario, "api-slo");
        assertEquals("performance-thresholds", slo.taskName());
        var latency = (Map<?, ?>) slo.parameters().get("latency");
        assertEquals(500, latency.get("p95"));
    }

    private static StepDefinition step(ScenarioDefinition scenario, String id) {
        return scenario.steps().stream()
                .filter(step -> step.id().value().equals(id))
                .findFirst()
                .orElseThrow();
    }
}

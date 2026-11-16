package com.performance.platform.scenario.parser;

import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.scenario.validation.DefaultScenarioValidator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Assertion description and validation")
class AssertionDescriptionParseTest {

    private final YamlScenarioParser parser = new YamlScenarioParser();
    private final DefaultScenarioValidator validator = new DefaultScenarioValidator();

    private static final String SCENARIO = """
            scenario:
              id: slo-scenario
              name: SLO scenario
              version: "1.0.0"
              steps:
                - id: api-slo
                  task: performance-thresholds
                  phase: ASSERTION
                  description:
                    title: API latency
                    sli: p95 of POST /orders
                    slo: p95 <= 500 ms
                    rationale: customer contract
                  parameters:
                    latency:
                      p95: 500
                      severity: CRITICAL
                - id: orders-updated
                  task: direct-assertion
                  phase: ASSERTION
                  description:
                    title: Orders processed
                    sli: rows moved from CREATED to DONE
                    slo: at least 1000 orders
                  parameters:
                    metric: database.updates
                    query: "UPDATE orders SET status='DONE' WHERE status='CREATED'"
                    operator: GTE
                    threshold: 1000
            """;

    @Test
    @DisplayName("step-level description is parsed and carried in parameters")
    void descriptionIsParsed() {
        ScenarioDefinition scenario = parser.parse(SCENARIO);

        StepDefinition slo = step(scenario, "api-slo");
        assertTrue(slo.parameters().containsKey("description"));
        var description = (Map<?, ?>) slo.parameters().get("description");
        assertEquals("API latency", description.get("title"));
        assertEquals("p95 <= 500 ms", description.get("slo"));
    }

    @Test
    @DisplayName("a valid new-model scenario has no validation errors")
    void validScenarioPassesValidation() {
        var result = validator.validate(parser.parse(SCENARIO));

        assertTrue(result.errors().isEmpty(), () -> "errors: " + result.errors());
    }

    @Test
    @DisplayName("an unknown metric is a scenario error, not a runtime error")
    void unknownMetricIsRejected() {
        String invalid = SCENARIO.replace("metric: database.updates", "metric: unknown.metric");

        var result = validator.validate(parser.parse(invalid));

        assertTrue(result.errors().stream()
                        .anyMatch(error -> error.message().contains("Unknown metric")),
                () -> "errors: " + result.errors());
    }

    @Test
    @DisplayName("EQ on a continuous metric without tolerance is rejected at parse time")
    void equalityWithoutToleranceIsRejected() {
        String invalid = SCENARIO
                .replace("metric: database.updates", "metric: database.scalar")
                .replace("operator: GTE", "operator: EQ");

        var result = validator.validate(parser.parse(invalid));

        assertTrue(result.errors().stream()
                        .anyMatch(error -> error.message().contains("tolerance")),
                () -> "errors: " + result.errors());
    }

    private static StepDefinition step(ScenarioDefinition scenario, String id) {
        return scenario.steps().stream()
                .filter(step -> step.id().value().equals(id))
                .findFirst()
                .orElseThrow();
    }
}

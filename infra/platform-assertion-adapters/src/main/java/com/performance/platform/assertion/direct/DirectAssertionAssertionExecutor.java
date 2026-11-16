package com.performance.platform.assertion.direct;

import com.performance.platform.assertion.AssertionSummaryFactory;
import com.performance.platform.assertion.AbstractCheckAssertionExecutor;
import com.performance.platform.assertion.CheckEvaluator;
import com.performance.platform.assertion.OperatorResolver;
import com.performance.platform.assertion.ParameterBinder;
import com.performance.platform.assertion.measure.DirectMeasurementResolver;
import com.performance.platform.domain.assertion.AssertionFamily;
import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.AssertionSummary;
import com.performance.platform.domain.assertion.AssertionSummaries;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.Expectation;
import com.performance.platform.domain.assertion.MetricCatalog;
import com.performance.platform.domain.assertion.MetricDefinition;
import com.performance.platform.domain.assertion.Observation;
import com.performance.platform.domain.assertion.Rollup;
import com.performance.platform.domain.assertion.Severity;
import com.performance.platform.domain.assertion.SloStatement;
import com.performance.platform.domain.assertion.TimeWindow;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.plugin.Assertion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assertion directe : volumetrie, nombre de lignes modifiees, compteur
 * d'appels recus, etat d'un fichier.
 * <p>
 * Une seule metrique par step, resolue par le catalogue canonique. L'adapter
 * ne compare rien : il produit une {@link Observation}, le moteur applique
 * l'{@link Expectation}.
 * <pre>
 * metric: database.updates
 * query: "UPDATE orders SET status='DONE' WHERE status='CREATED'"
 * operator: GTE
 * threshold: 1000
 * </pre>
 */
@Component
@Assertion(name = "direct-assertion",
        description = "Direct metric assertion: volumetry, updates, calls, file state")
public class DirectAssertionAssertionExecutor extends AbstractCheckAssertionExecutor {

    private static final Logger log =
            LoggerFactory.getLogger(DirectAssertionAssertionExecutor.class);
    private static final String TASK_NAME = "direct-assertion";

    private final DirectMeasurementResolver resolver;

    public DirectAssertionAssertionExecutor(DirectMeasurementResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public String getSupportedAssertionName() {
        return TASK_NAME;
    }

    @Override
    protected AssertionSummary evaluateChecks(ExecutionContext context,
                                                             StepDefinition step) {
        Instant start = Instant.now();
        SloStatement slo = AssertionSummaries.sloStatement(step);
        Map<String, Object> referenceParameters = referenceParameters(step);
        ParameterBinder params = ParameterBinder.of(referenceParameters);
        Rollup rollup = Rollup.all();
        List<Check> checks;

        try {
            String metricName = params.requireString("metric");
            MetricDefinition definition = MetricCatalog.require(metricName);
            Expectation expectation = expectation(definition, params);
            TimeWindow window = DirectMeasurementResolver.window(start, Duration.ZERO);
            Observation observation = resolver.resolve(definition, context, referenceParameters, window);
            String intent = params.optionalString("intent",
                    definition.id() + " " + expectation.describe(definition.unit()));
            checks = List.of(CheckEvaluator.evaluate(observation, expectation,
                    definition.id(), intent));
        } catch (IllegalArgumentException e) {
            log.warn("action=direct_assertion_error executionId={} assertionId={} error={}",
                    context.executionId().value(), step.id().value(), e.getMessage());
            checks = List.of(new Check(
                    com.performance.platform.domain.assertion.MetricId.of("direct.unknown"),
                    null, null, AssertionStatus.ERROR, "direct assertion", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("action=direct_assertion_failure executionId={} assertionId={}",
                    context.executionId().value(), step.id().value(), e);
            checks = List.of(new Check(
                    com.performance.platform.domain.assertion.MetricId.of("direct.unknown"),
                    null, null, AssertionStatus.ERROR, "direct assertion", e.getMessage()));
        }

        return AssertionSummaryFactory.build(step.id(), AssertionFamily.DIRECT, slo, rollup,
                checks, Duration.between(start, Instant.now()), Instant.now());
    }

    // ------------------------------------------------------------------

    /**
     * ADR-053 : {@code ref} est un champ de premiere classe de l'etape.
     * Il est transmis au resolveur sous la cle interne {@code refTaskId},
     * sans plus apparaitre dans les parametres YAML.
     */
    private static Map<String, Object> referenceParameters(StepDefinition step) {
        if (step.ref() == null) {
            return step.parameters();
        }
        var parameters = new HashMap<String, Object>(step.parameters());
        parameters.put("refTaskId", step.ref().value());
        return Map.copyOf(parameters);
    }

    /**
     * Construit l'attente :
     * <ul>
     *   <li>metrique STATE ({@code filesystem.exists}, {@code filesystem.checksum}) :
     *       {@code EQ 1.0}, le resolveur ayant deja converti l'etat en 1/0 ;</li>
     *   <li>sinon : operateur + seuil explicites (obligatoires).</li>
     * </ul>
     */
    private static Expectation expectation(MetricDefinition definition, ParameterBinder params) {
        Severity severity = params.optionalSeverity("severity", Severity.CRITICAL);
        double tolerance = params.optionalDouble("tolerance", 0.0);
        if (definition.kind() == com.performance.platform.domain.assertion.MetricKind.STATE) {
            return new Expectation(AssertionOperator.EQ, 1.0, 0.0, severity);
        }
        AssertionOperator operator = OperatorResolver.resolve(params.requireString("operator"));
        double threshold = params.requireDouble("threshold");
        if ((operator == AssertionOperator.EQ || operator == AssertionOperator.NEQ)
                && tolerance == 0 && definition.requiresToleranceForEquality()) {
            throw new IllegalArgumentException(
                    "EQ/NEQ on metric '" + definition.id() + "' requires a positive tolerance");
        }
        return new Expectation(operator, threshold, tolerance, severity);
    }
}

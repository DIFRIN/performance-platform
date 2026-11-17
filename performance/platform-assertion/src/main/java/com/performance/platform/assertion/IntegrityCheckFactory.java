package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.Expectation;
import com.performance.platform.domain.assertion.IntegrityTolerances;
import com.performance.platform.domain.assertion.MetricId;
import com.performance.platform.domain.assertion.Observation;
import com.performance.platform.domain.assertion.Severity;
import com.performance.platform.domain.assertion.TimeWindow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Produit les controles d'integrite d'une etape d'injection (ADR-069). Fonction pure. */
public final class IntegrityCheckFactory {

    static final MetricId ARRIVAL_DEVIATION = MetricId.of("injection.arrival.deviation");
    static final MetricId AGENT_LOSS = MetricId.of("injection.agent.loss");
    static final MetricId INCOMPLETE_RUN = MetricId.of("injection.run.incomplete");
    static final MetricId GENERATOR_CPU = MetricId.of("injection.generator.cpu");

    private static final TimeWindow NO_WINDOW = TimeWindow.of(Instant.EPOCH, Instant.EPOCH);
    private static final String SOURCE = "integrity";

    private IntegrityCheckFactory() {
    }

    public static List<Check> checks(IntegrityInput input, IntegrityTolerances tolerances) {
        Objects.requireNonNull(input, "input required");
        Objects.requireNonNull(tolerances, "tolerances required");
        return List.of(
                arrivalDeviation(input, tolerances),
                agentLoss(input),
                incompleteRun(input),
                generatorSaturation(input, tolerances));
    }

    private static Check arrivalDeviation(IntegrityInput in, IntegrityTolerances tol) {
        String intent = "arrival-deviation";
        if (!in.arrivalKnown() || !Double.isFinite(in.arrivalDeviationPercent())) {
            return Check.inconclusive(ARRIVAL_DEVIATION, intent,
                    "arrival deviation not measurable (opaque simulation): human review required");
        }
        Observation obs = Observation.of(ARRIVAL_DEVIATION, in.arrivalDeviationPercent(),
                "percent", NO_WINDOW, SOURCE);
        Expectation exp = Expectation.atMost(tol.maxArrivalDeviationPercent(), Severity.CRITICAL);
        return evaluate(obs, exp, intent, "arrival deviation " + in.arrivalDeviationPercent()
                + " % exceeds tolerance " + tol.maxArrivalDeviationPercent() + " %");
    }

    private static Check agentLoss(IntegrityInput in) {
        int lost = Math.max(0, in.grantedAgents() - in.reportingAgents());
        Observation obs = Observation.of(AGENT_LOSS, lost, "count", NO_WINDOW, SOURCE);
        return evaluate(obs, Expectation.atMost(0, Severity.CRITICAL), "agent-loss",
                lost + " of " + in.grantedAgents() + " granted agents returned no result");
    }

    private static Check incompleteRun(IntegrityInput in) {
        Observation obs = Observation.of(INCOMPLETE_RUN, in.complete() ? 0 : 1, "count",
                NO_WINDOW, SOURCE);
        return evaluate(obs, Expectation.atMost(0, Severity.CRITICAL), "incomplete-run",
                "worker stopped before the plan ended");
    }

    private static Check generatorSaturation(IntegrityInput in, IntegrityTolerances tol) {
        String intent = "generator-saturation";
        if (!Double.isFinite(in.generatorCpuPercent())) {
            return Check.inconclusive(GENERATOR_CPU, intent, "generator CPU not measurable");
        }
        Observation obs = Observation.of(GENERATOR_CPU, in.generatorCpuPercent(), "percent",
                NO_WINDOW, SOURCE);
        Expectation exp = Expectation.atMost(tol.maxGeneratorCpuPercent(), Severity.WARNING);
        return evaluate(obs, exp, intent, "generator CPU " + in.generatorCpuPercent()
                + " % exceeds threshold " + tol.maxGeneratorCpuPercent() + " %");
    }

    private static Check evaluate(Observation obs, Expectation exp, String intent, String failure) {
        return exp.isSatisfiedBy(obs.value())
                ? Check.passed(obs, exp, intent)
                : Check.failed(obs, exp, intent, failure);
    }
}

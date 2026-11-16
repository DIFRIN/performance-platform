package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.CriterionType;
import com.performance.platform.domain.assertion.Expectation;
import com.performance.platform.domain.assertion.MetricId;
import com.performance.platform.domain.assertion.Observation;
import com.performance.platform.domain.assertion.SamplePoint;
import com.performance.platform.domain.assertion.SeriesObservation;
import com.performance.platform.domain.assertion.Severity;
import com.performance.platform.domain.assertion.TemporalCriterion;
import com.performance.platform.domain.assertion.TimeWindow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Check evaluation")
class CheckEvaluatorTest {

    private static final MetricId P95 = MetricId.of("injection.latency.p95");
    private static final TimeWindow WINDOW = TimeWindow.of(Instant.EPOCH,
            Instant.EPOCH.plusSeconds(120));

    @Test
    @DisplayName("threshold evaluation produces PASSED or FAILED with an intent")
    void thresholdEvaluation() {
        var observation = Observation.of(P95, 412, "ms", WINDOW, "load");
        var expectation = Expectation.atMost(500, Severity.CRITICAL);

        Check passed = CheckEvaluator.evaluate(observation, expectation, P95, "p95 <= 500 ms");
        assertThat(passed.status()).isEqualTo(AssertionStatus.PASSED);
        assertThat(passed.intent()).isEqualTo("p95 <= 500 ms");

        var tooSlow = Observation.of(P95, 612, "ms", WINDOW, "load");
        Check failed = CheckEvaluator.evaluate(tooSlow, expectation, P95, null);
        assertThat(failed.status()).isEqualTo(AssertionStatus.FAILED);
        assertThat(failed.rationale()).contains("612");
    }

    @Test
    @DisplayName("missing measurement is INCONCLUSIVE, never PASSED")
    void missingMeasurement() {
        Check check = CheckEvaluator.evaluate(null, Expectation.atMost(500, Severity.WARNING),
                P95, "p95");
        assertThat(check.status()).isEqualTo(AssertionStatus.INCONCLUSIVE);
        assertThat(check.actualValue()).isNull();
    }

    @Test
    @DisplayName("temporal criteria: coverage first, then sustained and trend")
    void temporalCriteria() {
        var series = new SeriesObservation(P95, "ms", WINDOW, List.of(
                new SamplePoint(Instant.EPOCH, 100),
                new SamplePoint(Instant.EPOCH.plusSeconds(10), 120),
                new SamplePoint(Instant.EPOCH.plusSeconds(20), 140),
                new SamplePoint(Instant.EPOCH.plusSeconds(30), 160)), "load", null, null);

        Check coverage = TemporalCheckEvaluator.evaluate(series,
                new TemporalCriterion(CriterionType.COVERAGE, null, null, null, null, 30,
                        Severity.WARNING), "coverage", null);
        assertThat(coverage.status()).isEqualTo(AssertionStatus.INCONCLUSIVE);

        Check sustained = TemporalCheckEvaluator.evaluate(series,
                TemporalCriterion.sustained(AssertionOperator.LTE, 200, 1.0, 3, Severity.WARNING),
                "sustained", null);
        assertThat(sustained.status()).isEqualTo(AssertionStatus.PASSED);

        Check trend = TemporalCheckEvaluator.evaluate(series,
                TemporalCriterion.trend(1.0, 3.0, Severity.WARNING), "trend", null);
        assertThat(trend.status()).isEqualTo(AssertionStatus.PASSED);

        Check spread = TemporalCheckEvaluator.evaluate(series,
                TemporalCriterion.spread(0.0001, Severity.WARNING), "spread", null);
        assertThat(spread.status()).isEqualTo(AssertionStatus.FAILED);
    }

    @Test
    @DisplayName("recovery requires the injection end and returns INCONCLUSIVE without it")
    void recoveryNeedsInjectionEnd() {
        var series = new SeriesObservation(P95, "ms", WINDOW, List.of(
                new SamplePoint(Instant.EPOCH, 900),
                new SamplePoint(Instant.EPOCH.plusSeconds(30), 100),
                new SamplePoint(Instant.EPOCH.plusSeconds(45), 90)), "load", null, null);

        Check withoutEnd = TemporalCheckEvaluator.evaluate(series,
                TemporalCriterion.recovery(200, Duration.ofSeconds(60), Severity.WARNING),
                "recovery", null);
        assertThat(withoutEnd.status()).isEqualTo(AssertionStatus.INCONCLUSIVE);

        Check recovered = TemporalCheckEvaluator.evaluate(series,
                TemporalCriterion.recovery(200, Duration.ofSeconds(60), Severity.WARNING),
                "recovery", Instant.EPOCH);
        assertThat(recovered.status()).isEqualTo(AssertionStatus.PASSED);
    }
}

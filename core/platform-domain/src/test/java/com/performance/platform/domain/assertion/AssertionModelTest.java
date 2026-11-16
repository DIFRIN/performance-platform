package com.performance.platform.domain.assertion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Assertion model (ADR-049)")
class AssertionModelTest {

    @Test
    @DisplayName("catalog resolves canonical names and normalises units")
    void catalogResolvesCanonicalNames() {
        assertThat(MetricCatalog.require("injection.latency.p95").id().toString())
                .isEqualTo("injection.latency.p95");
        assertThat(MetricCatalog.require("kafka.messages.consumed").kind())
                .isEqualTo(MetricKind.COUNTER);
        assertThat(MetricCatalog.require("database.updates").unit()).isEqualTo("count");
        assertThat(MetricCatalog.find("p95")).isEmpty();
        assertThat(MetricCatalog.find("unknown.metric")).isEmpty();
        assertThat(MetricCatalog.normalizeUnit("%")).isEqualTo("percent");
        assertThat(MetricCatalog.normalizeUnit("req/s")).isEqualTo("req/s");
    }

    @Test
    @DisplayName("EQ requires an explicit tolerance on continuous metrics")
    void equalityUsesTolerance() {
        var exact = new Expectation(AssertionOperator.EQ, 0.3, 0.05, Severity.CRITICAL);
        assertThat(exact.isSatisfiedBy(0.32)).isTrue();
        assertThat(exact.isSatisfiedBy(0.40)).isFalse();
        assertThat(MetricCatalog.require("injection.error.rate").requiresToleranceForEquality())
                .isTrue();
        assertThat(MetricCatalog.require("injection.requests.total").requiresToleranceForEquality())
                .isFalse();
    }

    @Test
    @DisplayName("rollup: ALL is blocked by an inconclusive check, never a success")
    void rollupHandlesInconclusive() {
        var metric = MetricId.of("injection.latency.p95");
        var window = TimeWindow.of(Instant.EPOCH, Instant.EPOCH.plusSeconds(60));
        var observation = Observation.of(metric, 120, "ms", window, "test");
        var expectation = Expectation.atMost(500, Severity.CRITICAL);

        var passed = Check.passed(observation, expectation, "p95");
        var inconclusive = Check.inconclusive(MetricId.of("injection.throughput"),
                "throughput", "no data");

        assertThat(Rollup.all().combine(List.of(passed))).isEqualTo(AssertionStatus.PASSED);
        assertThat(Rollup.all().combine(List.of(passed, inconclusive)))
                .isEqualTo(AssertionStatus.INCONCLUSIVE);
        assertThat(Rollup.any().combine(List.of(passed, inconclusive)))
                .isEqualTo(AssertionStatus.PASSED);
        assertThat(Rollup.all().combine(List.of())).isEqualTo(AssertionStatus.INCONCLUSIVE);
    }

    @Test
    @DisplayName("series analysis computes duty cycle, spread and trend")
    void seriesAnalysis() {
        var metric = MetricId.of("injection.latency.p95");
        var window = TimeWindow.of(Instant.EPOCH, Instant.EPOCH.plusSeconds(4));
        var series = new SeriesObservation(metric, "ms", window, List.of(
                new SamplePoint(Instant.EPOCH, 100),
                new SamplePoint(Instant.EPOCH.plusSeconds(1), 110),
                new SamplePoint(Instant.EPOCH.plusSeconds(2), 120),
                new SamplePoint(Instant.EPOCH.plusSeconds(3), 130)), "test", null, null);

        assertThat(SeriesAnalysis.dutyCycle(series, AssertionOperator.LTE, 120).orElseThrow())
                .isEqualTo(0.75);
        assertThat(SeriesAnalysis.slopePerSecond(series).orElseThrow()).isEqualTo(10.0);
        assertThat(SeriesAnalysis.monotonic(series, true)).isTrue();
        assertThat(SeriesAnalysis.coefficientOfVariation(series)).isPresent();
        assertThat(SeriesAnalysis.maxGap(series).orElseThrow()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("summary keeps the textual SLO and no derived boolean property")
    void summaryCarriesSlo() {
        var slo = new SloStatement("API latency", "p95 of POST /orders",
                "p95 <= 500 ms", "customer contract");
        var summary = new AssertionSummary(
                com.performance.platform.domain.id.TaskId.of("api-slo"),
                AssertionStatus.PASSED, "ok", List.of(),
                Duration.ofMillis(1), Instant.now(), AssertionFamily.PERFORMANCE_THRESHOLDS,
                slo, List.of(), Rollup.all());

        assertThat(summary.slo().slo()).contains("500");
        assertThat(summary.slo().blank()).isFalse();
        assertThat(SloStatement.empty().blank()).isTrue();
        assertThat(summary.family()).isEqualTo(AssertionFamily.PERFORMANCE_THRESHOLDS);
    }
}

package com.performance.platform.domain.assertion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetricCatalogTest {

    @Test
    void latencyQuantilesAreMergedWithWorstAgentVariant() {
        for (String q : new String[] {"p50", "p75", "p90", "p95", "p99"}) {
            assertThat(MetricCatalog.require("injection.latency." + q).reduction())
                    .isEqualTo(Reduction.MERGE);
            assertThat(MetricCatalog.require("injection.latency." + q + ".worst-agent").reduction())
                    .isEqualTo(Reduction.MAX);
        }
    }

    @Test
    void arrivalAndGeneratorMetricsAreRegistered() {
        assertThat(MetricCatalog.require("injection.arrival.planned").reduction()).isEqualTo(Reduction.SUM);
        assertThat(MetricCatalog.require("injection.arrival.achieved").reduction()).isEqualTo(Reduction.SUM);
        assertThat(MetricCatalog.require("injection.arrival.deviation").unit()).isEqualTo("percent");
        assertThat(MetricCatalog.require("injection.start.skew").unit()).isEqualTo("ms");
        assertThat(MetricCatalog.require("injection.generator.cpu").reduction()).isEqualTo(Reduction.MAX);
        assertThat(MetricCatalog.require("injection.generator.cpu").unit()).isEqualTo("percent");
        assertThat(MetricCatalog.require("injection.generator.gc.pause").unit()).isEqualTo("ms");
    }
}

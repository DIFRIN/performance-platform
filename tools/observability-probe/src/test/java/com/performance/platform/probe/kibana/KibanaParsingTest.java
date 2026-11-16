package com.performance.platform.probe.kibana;

import com.fasterxml.jackson.databind.JsonNode;
import com.performance.platform.probe.Http;
import com.performance.platform.probe.model.ProbePoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Kibana / Elasticsearch response parsing")
class KibanaParsingTest {

    @Test
    @DisplayName("reads avg buckets from a date_histogram")
    void readsAvgBuckets() throws Exception {
        JsonNode response = Http.MAPPER.readTree("""
                {"aggregations": {"series": {"buckets": [
                  {"key": 1763024400000, "value": {"value": 412.5}},
                  {"key": 1763024460000, "value": {"value": 438.0}}
                ]}}}
                """);

        List<ProbePoint> points = KibanaClient.extractPoints(response,
                new KibanaClient.EsQuery("metrics-*", "@timestamp", "latency", "avg",
                        null, "1m", "latency", "ms"));

        assertEquals(2, points.size());
        assertEquals(412.5, points.get(0).value());
    }

    @Test
    @DisplayName("reads percentiles and keeps the worst percentile of a bucket")
    void readsPercentiles() throws Exception {
        JsonNode response = Http.MAPPER.readTree("""
                {"aggregations": {"series": {"buckets": [
                  {"key": 1, "value": {"values": {"50.0": 120.0, "95.0": 480.0, "99.0": 900.0}}}
                ]}}}
                """);

        List<ProbePoint> points = KibanaClient.extractPoints(response,
                new KibanaClient.EsQuery("metrics-*", "@timestamp", "latency", "percentiles",
                        95.0, "1m", "latency", "ms"));

        assertEquals(1, points.size());
        assertEquals(900.0, points.get(0).value());
    }

    @Test
    @DisplayName("query body contains the execution window and the aggregation")
    void buildsSearchBody() {
        var window = new com.performance.platform.probe.model.ProbeWindow(
                java.time.Instant.parse("2026-11-13T09:00:00Z"),
                java.time.Instant.parse("2026-11-13T09:20:00Z"), 60_000);

        JsonNode body = KibanaClient.buildSearchBody(
                new KibanaClient.EsQuery("metrics-*", "@timestamp", "duration", "percentiles",
                        95.0, "1m", "p95", "ms"), window);

        assertEquals(0, body.path("size").asInt());
        assertEquals("2026-11-13T09:00:00Z",
                body.at("/query/bool/filter/0/range/@timestamp/gte").asText());
        assertTrue(body.at("/aggs/series/aggs/value/percentiles").isObject());
        assertEquals("1m", body.at("/aggs/series/date_histogram/fixed_interval").asText());
    }
}

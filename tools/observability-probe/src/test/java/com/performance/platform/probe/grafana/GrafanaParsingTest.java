package com.performance.platform.probe.grafana;

import com.fasterxml.jackson.databind.JsonNode;
import com.performance.platform.probe.Http;
import com.performance.platform.probe.model.ProbePoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Grafana response parsing")
class GrafanaParsingTest {

    @Test
    @DisplayName("extracts time/value pairs from a Grafana frame")
    void extractsFramePoints() throws Exception {
        JsonNode frame = Http.MAPPER.readTree("""
                {
                  "schema": {"fields": [
                    {"name": "time", "type": "time"},
                    {"name": "A-series", "type": "number"}
                  ]},
                  "data": {"values": [[1763024400000, 1763024415000, 1763024430000],
                                      [412.0, 438.5, 431.0]]}
                }
                """);

        List<ProbePoint> points = GrafanaClient.extractPoints(frame);

        assertEquals(3, points.size());
        assertEquals(1763024400000L, points.get(0).timestamp());
        assertEquals(438.5, points.get(1).value());
    }

    @Test
    @DisplayName("ignores null values instead of turning them into zeros")
    void skipsNullValues() throws Exception {
        JsonNode frame = Http.MAPPER.readTree("""
                {
                  "schema": {"fields": [
                    {"name": "time", "type": "time"},
                    {"name": "value", "type": "number"}
                  ]},
                  "data": {"values": [[1, 2, 3], [10, null, 30]]}
                }
                """);

        List<ProbePoint> points = GrafanaClient.extractPoints(frame);

        assertEquals(2, points.size());
        assertEquals(10.0, points.get(0).value());
        assertEquals(30.0, points.get(1).value());
    }

    @Test
    @DisplayName("finds nested panels inside rows")
    void findsNestedPanel() throws Exception {
        JsonNode panels = Http.MAPPER.readTree("""
                [{"id": 1, "panels": [{"id": 12, "title": "Latency", "targets": []}]}]
                """);

        JsonNode panel = GrafanaClient.findPanel(panels, 12);

        assertNotNull(panel);
        assertEquals("Latency", panel.path("title").asText());
        assertNull(GrafanaClient.findPanel(panels, 99));
    }

    @Test
    @DisplayName("binds template variables without leaving placeholders")
    void substitutesVariables() {
        String replaced = GrafanaClient.replace(
                "sum(rate(http_requests_total{service=\"$service\",env=\"${env}\"}[2m]))",
                Map.of("service", "orders", "env", "prod"));

        assertTrue(replaced.contains("service=\"orders\""));
        assertTrue(replaced.contains("env=\"prod\""));
        assertTrue(!replaced.contains("$"));
    }
}

package com.performance.platform.probe;

import com.performance.platform.probe.model.ProbePoint;
import com.performance.platform.probe.model.ProbeSeries;
import com.performance.platform.probe.render.SvgChart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Probe CLI helpers")
class ProbeCliHelperTest {

    private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");

    @Test
    @DisplayName("parses relative windows")
    void parsesRelativeWindows() {
        assertEquals(NOW.minusSeconds(1800), TimeParser.parseInstant("now-30m", NOW));
        assertEquals(NOW, TimeParser.parseInstant("now", NOW));
        assertEquals(Instant.parse("2026-11-13T09:00:00Z"),
                TimeParser.parseInstant("2026-11-13T09:00:00Z", NOW));
        var window = TimeParser.window("now-10m", "now", "30s");
        assertEquals(30_000, window.resolutionMillis());
    }

    @Test
    @DisplayName("rejects an inverted window")
    void rejectsInvertedWindow() {
        assertThrows(ProbeException.class,
                () -> TimeParser.window("2026-11-13T10:00:00Z", "2026-11-13T09:00:00Z", "1m"));
    }

    @Test
    @DisplayName("parses --map entries")
    void parsesMappings() {
        var mapping = com.performance.platform.probe.model.QueryMapping
                .parse("refId=A:injection.latency.p95:ms");
        assertEquals("A", mapping.refId());
        assertEquals("injection.latency.p95", mapping.metric());
        assertEquals("ms", mapping.unit());
        assertThrows(IllegalArgumentException.class,
                () -> com.performance.platform.probe.model.QueryMapping.parse("bad"));
    }

    @Test
    @DisplayName("renders a deterministic SVG chart and an explicit no-data state")
    void rendersSvg() {
        var series = new ProbeSeries("injection.latency.p95", "ms", List.of(
                new ProbePoint(NOW.toEpochMilli(), 412),
                new ProbePoint(NOW.plusSeconds(15).toEpochMilli(), 438)));

        String first = SvgChart.render("Latency", List.of(series));
        String second = SvgChart.render("Latency", List.of(series));
        assertEquals(first, second);
        assertTrue(first.contains("<svg"));
        assertTrue(first.contains("polyline"));

        String empty = SvgChart.render("Latency", List.of(new ProbeSeries("x", "ms", List.of())));
        assertTrue(empty.contains("No data in window"));
    }

    @Test
    @DisplayName("unknown command exits with code 2")
    void unknownCommand() {
        assertEquals(2, ProbeCli.run(new String[]{"nope"}));
        assertEquals(0, ProbeCli.run(new String[]{"help"}));
    }
}

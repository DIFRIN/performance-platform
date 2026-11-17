package com.performance.platform.assertion;

import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.injection.LatencyHistogram;
import com.performance.platform.domain.injection.LoadWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Agrège les contributions d'injection par agent (ADR-068) : percentiles globaux par fusion
 * d'histogrammes (MERGE), débit sur fenêtre commune mesurée, arrivées, skew de démarrage, santé.
 * Sans état, sans dépendance framework.
 */
public final class InjectionAggregator {

    private static final String[] PERCENTILES = {"p50", "p75", "p90", "p95", "p99"};
    private static final double[] QUANTILES = {0.50, 0.75, 0.90, 0.95, 0.99};

    /** Valeur d'une métrique ; {@code value == null} signifie INCONCLUSIVE (avec raison). */
    public record Measure(Double value, String inconclusiveReason) {
        public static Measure of(double v) {
            return new Measure(v, null);
        }

        public static Measure inconclusive(String reason) {
            return new Measure(null, reason);
        }

        public boolean isInconclusive() {
            return value == null;
        }
    }

    /** Métriques réduites, indexées par identifiant du catalogue. */
    public record Aggregate(Map<String, Measure> metrics) {
        public Aggregate {
            metrics = Map.copyOf(metrics);
        }

        public Measure get(String metric) {
            Measure m = metrics.get(metric);
            if (m == null) {
                throw new IllegalArgumentException("unknown metric: " + metric);
            }
            return m;
        }
    }

    private InjectionAggregator() {
    }

    public static Aggregate aggregate(List<InjectionResult> contributions) {
        Objects.requireNonNull(contributions, "contributions required");
        if (contributions.isEmpty()) {
            throw new IllegalArgumentException("at least one contribution required");
        }
        List<InjectionResult> rs = List.copyOf(contributions);
        Map<String, Measure> out = new LinkedHashMap<>();

        boolean allHistograms = rs.stream().allMatch(r -> r.histogram().count() > 0);
        LatencyHistogram merged = LatencyHistogram.empty();
        if (allHistograms) {
            for (InjectionResult r : rs) {
                merged = merged.merge(r.histogram());
            }
        }
        for (int i = 0; i < PERCENTILES.length; i++) {
            String key = "injection.latency." + PERCENTILES[i];
            final int idx = i;
            if (rs.size() == 1 && !allHistograms) {
                out.put(key, Measure.of(percentileOf(rs.get(0), idx)));
            } else if (allHistograms) {
                out.put(key, Measure.of(merged.quantile(QUANTILES[i])));
            } else {
                out.put(key, Measure.inconclusive("histogram missing for at least one agent"));
            }
            out.put(key + ".worst-agent",
                Measure.of(rs.stream().mapToLong(r -> percentileOf(r, idx)).max().orElse(0)));
        }

        long requests = rs.stream().mapToLong(InjectionResult::totalRequests).sum();
        long failed = rs.stream().mapToLong(InjectionResult::failedRequests).sum();
        out.put("injection.requests.total", Measure.of(requests));
        out.put("injection.requests.failed", Measure.of(failed));
        out.put("injection.error.rate", Measure.of(InjectionResult.computeErrorRate(requests, failed)));

        out.put("injection.throughput", throughput(rs));
        arrivals(rs, out);
        out.put("injection.start.skew", skew(rs));
        out.put("injection.generator.cpu",
            Measure.of(rs.stream().mapToDouble(r -> r.generatorHealth().cpuLoad()).max().orElse(0)));
        out.put("injection.generator.gc.pause",
            Measure.of(rs.stream().mapToLong(r -> r.generatorHealth().gcPause().toMillis()).max().orElse(0)));
        return new Aggregate(out);
    }

    private static long percentileOf(InjectionResult r, int idx) {
        return switch (idx) {
            case 0 -> r.p50Ms();
            case 1 -> r.p75Ms();
            case 2 -> r.p90Ms();
            case 3 -> r.p95Ms();
            default -> r.p99Ms();
        };
    }

    private static Measure throughput(List<InjectionResult> rs) {
        boolean windowed = rs.stream().allMatch(r -> !r.windows().isEmpty());
        if (!windowed) {
            return rs.size() == 1
                ? Measure.of(rs.get(0).throughput())
                : Measure.inconclusive("windows missing for at least one agent");
        }
        // Intersection des débuts de fenêtres mesurées, bornée à l'intervalle couvert par tous.
        Instant from = rs.stream().map(InjectionResult::startedAt).max(Instant::compareTo).orElseThrow();
        Instant to = rs.stream().map(InjectionResult::endedAt).min(Instant::compareTo).orElseThrow();
        boolean bounded = !from.equals(Instant.EPOCH) && !to.equals(Instant.EPOCH);

        Set<Instant> common = null;
        for (InjectionResult r : rs) {
            Set<Instant> starts = new TreeSet<>();
            for (LoadWindow w : r.windows()) {
                if (w.measured() && (!bounded || (!w.start().isBefore(from) && !w.end().isAfter(to)))) {
                    starts.add(w.start());
                }
            }
            if (common == null) {
                common = starts;
            } else {
                common.retainAll(starts);
            }
        }
        if (common == null || common.isEmpty()) {
            return Measure.inconclusive("no common measured window");
        }
        long count = 0;
        Duration length = Duration.ZERO;
        boolean first = true;
        for (InjectionResult r : rs) {
            for (LoadWindow w : r.windows()) {
                if (w.measured() && common.contains(w.start())) {
                    count += w.requests();
                    if (first) {
                        length = length.plus(w.length());
                    }
                }
            }
            first = false;
        }
        double seconds = length.toMillis() / 1000.0;
        return seconds <= 0 ? Measure.inconclusive("empty common window") : Measure.of(count / seconds);
    }

    private static void arrivals(List<InjectionResult> rs, Map<String, Measure> out) {
        List<LoadWindow> measured = new ArrayList<>();
        for (InjectionResult r : rs) {
            r.windows().stream().filter(LoadWindow::measured).forEach(measured::add);
        }
        if (measured.isEmpty()) {
            String why = "no measured window";
            out.put("injection.arrival.planned", Measure.inconclusive(why));
            out.put("injection.arrival.achieved", Measure.inconclusive(why));
            out.put("injection.arrival.deviation", Measure.inconclusive(why));
            return;
        }
        long planned = measured.stream().mapToLong(LoadWindow::plannedArrivals).sum();
        long achieved = measured.stream().mapToLong(LoadWindow::startedUsers).sum();
        out.put("injection.arrival.planned", Measure.of(planned));
        out.put("injection.arrival.achieved", Measure.of(achieved));
        out.put("injection.arrival.deviation", planned == 0
            ? Measure.inconclusive("planned arrivals unknown or zero")
            : Measure.of(Math.abs((double) achieved - planned) / planned * 100.0));
    }

    private static Measure skew(List<InjectionResult> rs) {
        if (rs.stream().anyMatch(r -> r.startedAt().equals(Instant.EPOCH))) {
            return rs.size() == 1 ? Measure.of(0) : Measure.inconclusive("startedAt unknown");
        }
        Instant min = rs.stream().map(InjectionResult::startedAt).min(Instant::compareTo).orElseThrow();
        Instant max = rs.stream().map(InjectionResult::startedAt).max(Instant::compareTo).orElseThrow();
        return Measure.of(Duration.between(min, max).toMillis());
    }
}

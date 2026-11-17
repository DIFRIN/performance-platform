package com.performance.platform.reporting.engine;

import com.performance.platform.domain.injection.GeneratorHealth;
import com.performance.platform.domain.injection.LatencyHistogram;
import com.performance.platform.domain.injection.LoadWindow;
import com.performance.platform.reporting.model.InjectionReportEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Section « injection » du rapport (ADR-068) : charge planifiée vs réalisée, percentiles globaux
 * (histogramme fusionné) et worst-agent, contributions par agent, santé du générateur, preuves.
 * Record immuable, 0 dépendance framework.
 */
public record InjectionMeasurementsSection(
        List<TimelinePoint> timeline,
        Map<String, Long> globalPercentiles,
        Map<String, Long> worstAgentPercentiles,
        List<AgentContribution> agents,
        List<HealthEntry> health,
        Evidence evidence) {

    public static final List<Double> QUANTILES = List.of(0.50, 0.75, 0.90, 0.95, 0.99);

    public InjectionMeasurementsSection {
        timeline = List.copyOf(timeline);
        globalPercentiles = globalPercentiles == null ? null : Map.copyOf(globalPercentiles);
        worstAgentPercentiles = Map.copyOf(worstAgentPercentiles);
        agents = List.copyOf(agents);
        health = List.copyOf(health);
    }

    /** Vrai si les percentiles globaux sont disponibles (tous les agents ont un histogramme). */
    public boolean globalAvailable() { return globalPercentiles != null; }

    /** Point de chronologie, fenêtres sommées sur tous les agents. {@code label} = « non mesuré » pour le warm-up. */
    public record TimelinePoint(Instant start, Duration length, long planned, long achieved,
                                long requests, long failures, boolean measured, String label) {}

    public record AgentContribution(String agentId, long requests, long failures, double requestShare) {}

    public record HealthEntry(String agentId, double cpuLoad, Duration gcPause, long peakHeapBytes) {}

    /** Preuves stockées avec le rapport (ADR-051) : permettent de recalculer les percentiles. */
    public record Evidence(LatencyHistogram mergedHistogram, List<LoadWindow> aggregatedWindows) {
        public Evidence {
            aggregatedWindows = List.copyOf(aggregatedWindows);
        }
    }

    /** Forme canonique (Map/List/Number/String/Boolean) pour {@code CampaignReport.injectionMeasurements}. */
    public Map<String, Object> toMap() {
        var out = new LinkedHashMap<String, Object>();
        var tl = new ArrayList<Object>();
        for (var t : timeline) {
            var m = new LinkedHashMap<String, Object>();
            m.put("start", t.start().toString());
            m.put("lengthMs", t.length().toMillis());
            m.put("planned", t.planned());
            m.put("achieved", t.achieved());
            m.put("requests", t.requests());
            m.put("failures", t.failures());
            m.put("measured", t.measured());
            m.put("label", t.label());
            tl.add(m);
        }
        out.put("timeline", tl);
        out.put("globalAvailable", globalAvailable());
        if (globalPercentiles != null) out.put("globalPercentiles", new LinkedHashMap<>(globalPercentiles));
        out.put("worstAgentPercentiles", new LinkedHashMap<>(worstAgentPercentiles));
        var ag = new ArrayList<Object>();
        for (var a : agents) {
            ag.add(new LinkedHashMap<>(Map.of("agentId", a.agentId(), "requests", a.requests(),
                    "failures", a.failures(), "requestShare", a.requestShare())));
        }
        out.put("agents", ag);
        var hl = new ArrayList<Object>();
        for (var h : health) {
            hl.add(new LinkedHashMap<>(Map.of("agentId", h.agentId(), "cpuLoad", h.cpuLoad(),
                    "gcPauseMs", h.gcPause().toMillis(), "peakHeapBytes", h.peakHeapBytes())));
        }
        out.put("health", hl);
        var ev = new LinkedHashMap<String, Object>();
        ev.put("mergedHistogram", evidence.mergedHistogram().toMap());
        var ws = new ArrayList<Object>();
        for (var w : evidence.aggregatedWindows()) {
            var m = new LinkedHashMap<String, Object>();
            m.put("start", w.start().toString());
            m.put("lengthMs", w.length().toMillis());
            m.put("plannedArrivals", w.plannedArrivals());
            m.put("startedUsers", w.startedUsers());
            m.put("requests", w.requests());
            m.put("failures", w.failures());
            m.put("measured", w.measured());
            ws.add(m);
        }
        ev.put("aggregatedWindows", ws);
        out.put("evidence", ev);
        return out;
    }

    public static InjectionMeasurementsSection build(List<InjectionReportEntry> entries) {
        // Chronologie : somme par début de fenêtre (horloge murale)
        Map<Instant, long[]> sums = new TreeMap<>();
        Map<Instant, Duration> lengths = new TreeMap<>();
        Map<Instant, Boolean> measured = new TreeMap<>();
        for (var entry : entries) {
            for (LoadWindow w : entry.metrics().windows()) {
                long[] s = sums.computeIfAbsent(w.start(), k -> new long[4]);
                s[0] += w.plannedArrivals();
                s[1] += w.startedUsers();
                s[2] += w.requests();
                s[3] += w.failures();
                lengths.putIfAbsent(w.start(), w.length());
                measured.merge(w.start(), w.measured(), Boolean::logicalOr);
            }
        }
        var timeline = new ArrayList<TimelinePoint>();
        var aggregated = new ArrayList<LoadWindow>();
        sums.forEach((start, s) -> {
            boolean m = measured.get(start);
            timeline.add(new TimelinePoint(start, lengths.get(start), s[0], s[1], s[2], s[3], m,
                    m ? "mesuré" : "non mesuré"));
            aggregated.add(new LoadWindow(start, lengths.get(start), s[0], s[1], s[2], s[3], m));
        });

        // Histogramme fusionné ; indisponible si un agent n'en a pas (jamais recalculé)
        LatencyHistogram merged = LatencyHistogram.empty();
        boolean available = !entries.isEmpty();
        for (var entry : entries) {
            var r = entry.metrics();
            if (r.histogram().count() == 0 && r.totalRequests() > 0) available = false;
            merged = merged.merge(r.histogram());
        }
        Map<String, Long> global = null;
        if (available && merged.count() > 0) {
            global = new LinkedHashMap<>();
            for (double q : QUANTILES) global.put(label(q), merged.quantile(q));
        }

        // Worst-agent : maximum des percentiles par agent
        Map<String, Long> worst = new LinkedHashMap<>();
        worst.put("p50", max(entries, 50));
        worst.put("p75", max(entries, 75));
        worst.put("p90", max(entries, 90));
        worst.put("p95", max(entries, 95));
        worst.put("p99", max(entries, 99));

        long total = entries.stream().mapToLong(e -> e.metrics().totalRequests()).sum();
        var agents = new ArrayList<AgentContribution>();
        var health = new ArrayList<HealthEntry>();
        for (var entry : entries) {
            var r = entry.metrics();
            String id = entry.agentId() == null ? "local" : entry.agentId();
            agents.add(new AgentContribution(id, r.totalRequests(), r.failedRequests(),
                    total == 0 ? 0.0 : (double) r.totalRequests() / total));
            GeneratorHealth h = r.generatorHealth();
            health.add(new HealthEntry(id, h.cpuLoad(), h.gcPause(), h.peakHeapBytes()));
        }
        agents.sort(Comparator.comparing(AgentContribution::agentId));
        return new InjectionMeasurementsSection(timeline, global, worst, agents, health,
                new Evidence(merged, aggregated));
    }

    private static String label(double q) { return "p" + Math.round(q * 100); }

    private static long max(List<InjectionReportEntry> entries, int p) {
        return entries.stream().mapToLong(e -> switch (p) {
            case 50 -> e.metrics().p50Ms();
            case 75 -> e.metrics().p75Ms();
            case 90 -> e.metrics().p90Ms();
            case 95 -> e.metrics().p95Ms();
            default -> e.metrics().p99Ms();
        }).max().orElse(0);
    }
}

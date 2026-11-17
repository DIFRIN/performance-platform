package com.performance.platform.injection.gatling.result;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.injection.LatencyHistogram;
import com.performance.platform.domain.injection.LoadWindow;
import com.performance.platform.domain.injection.Stage;
import com.performance.platform.domain.injection.WorkloadPlan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads Gatling 3.11's tab-separated RUN, USER and REQUEST records (LogFileDataWriter).
 * A latency histogram keeps memory proportional to distinct millisecond latencies, not requests.
 */
final class SimulationLogResultParser {

    static final Duration DEFAULT_WINDOW = Duration.ofSeconds(5);

    /** Fenêtre {@code injection.window} (propriété, puis env {@code INJECTION_WINDOW} prioritaire). */
    static Duration configuredWindow() {
        String value = System.getenv("INJECTION_WINDOW");
        if (value == null || value.isBlank()) value = System.getProperty("injection.window");
        if (value == null || value.isBlank()) return DEFAULT_WINDOW;
        value = value.trim().toLowerCase();
        try {
            Duration d = value.startsWith("p")
                    ? Duration.parse(value)
                    : value.endsWith("ms") ? Duration.ofMillis(Long.parseLong(value.replace("ms", "")))
                    : value.endsWith("s") ? Duration.ofSeconds(Long.parseLong(value.replace("s", "")))
                    : Duration.ofSeconds(Long.parseLong(value));
            return d.isPositive() ? d : DEFAULT_WINDOW;
        } catch (RuntimeException e) {
            return DEFAULT_WINDOW;
        }
    }

    InjectionResult parse(Path directory, TaskId taskId) {
        return parse(directory, taskId, null, configuredWindow());
    }

    /** {@code plan} nul : toutes les fenêtres sont mesurées, arrivées planifiées inconnues. */
    InjectionResult parse(Path directory, TaskId taskId, WorkloadPlan plan, Duration window) {
        long windowMs = window.toMillis();
        if (windowMs <= 0) throw new IllegalArgumentException("window must be >= 1 ms");
        long runStart = -1;
        var requestsRaw = new ArrayList<long[]>(); // {startMs, endMs, ko}
        var userStarts = new ArrayList<Long>();
        var latencies = new TreeMap<Long, Long>();
        String simulationClass = null;
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        long total = 0;
        long failed = 0;
        double sum = 0;
        long lineNumber = 0;
        Path log = directory.resolve("simulation.log");

        try (var reader = Files.newBufferedReader(log)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] fields = line.split("\t", -1);
                switch (fields[0]) {
                    case "RUN" -> {
                        if (fields.length < 6 || fields[1].isBlank() || simulationClass != null) {
                            throw new IllegalArgumentException("invalid or duplicate RUN record");
                        }
                        simulationClass = fields[1];
                        runStart = fields[3].isBlank() ? -1 : timestamp(fields[3]);
                    }
                    case "USER" -> {
                        if (fields.length != 4
                                || !(fields[2].equals("START") || fields[2].equals("END"))) {
                            throw new IllegalArgumentException("invalid USER record");
                        }
                        long timestamp = timestamp(fields[3]);
                        first = Math.min(first, timestamp);
                        last = Math.max(last, timestamp);
                        if (fields[2].equals("START")) userStarts.add(timestamp);
                    }
                    case "REQUEST" -> {
                        if (fields.length < 7
                                || !(fields[5].equals("OK") || fields[5].equals("KO"))) {
                            throw new IllegalArgumentException("invalid REQUEST record");
                        }
                        long start = timestamp(fields[3]);
                        long end = timestamp(fields[4]);
                        if (end < start) {
                            throw new IllegalArgumentException("request ends before it starts");
                        }
                        requestsRaw.add(new long[] {start, end, fields[5].equals("KO") ? 1 : 0});
                        total++;
                        if (fields[5].equals("KO")) failed++;
                        first = Math.min(first, start);
                        last = Math.max(last, end);
                    }
                    default -> {
                        // GROUP, ERROR and ASSERTION records are not additional requests.
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new ResultParsingException(
                    "Failed to read " + log + " at line " + lineNumber + ": " + e.getMessage(), e);
        }

        if (simulationClass == null || total == 0 || last <= first) {
            throw new ResultParsingException(
                    "Incomplete simulation.log in " + directory
                            + ": requires RUN, REQUEST records and a positive measured duration");
        }
        long planStart = runStart >= 0 ? runStart : first;
        List<Stage> stages = plan == null ? List.of() : plan.stages();
        long[] stageEnds = new long[stages.size()];
        long cursor = 0;
        for (int i = 0; i < stageEnds.length; i++) {
            cursor += stages.get(i).duration().toMillis();
            stageEnds[i] = cursor;
        }
        var planned = new PlannedArrivals(plan, planStart);

        // Fenêtres : [floor(min), fin du log ou du plan]
        long planEnd = plan == null ? last : Math.max(last, planStart + Math.max(cursor - 1, 0));
        long firstWindow = Math.floorDiv(Math.min(first, planStart), windowMs) * windowMs;
        long lastWindow = Math.floorDiv(planEnd, windowMs) * windowMs;
        long windowCount = (lastWindow - firstWindow) / windowMs + 1;
        if (windowCount > 1_000_000) {
            throw new ResultParsingException("Too many windows (" + windowCount + ") for window " + window);
        }
        int n = (int) windowCount;
        long[] winRequests = new long[n];
        long[] winFailures = new long[n];
        long[] winUsers = new long[n];
        boolean[] winMeasured = new boolean[n];
        for (int i = 0; i < n; i++) {
            long start = firstWindow + i * windowMs;
            winMeasured[i] = measuredAt(stages, stageEnds, Math.max(start, planStart) - planStart);
        }
        for (long u : userStarts) winUsers[(int) ((Math.floorDiv(u, windowMs) * windowMs - firstWindow) / windowMs)]++;

        var ok = new TreeMap<Long, Long>();
        var ko = new TreeMap<Long, Long>();
        long measuredTotal = 0;
        for (long[] r : requestsRaw) {
            int w = (int) ((Math.floorDiv(r[0], windowMs) * windowMs - firstWindow) / windowMs);
            winRequests[w]++;
            if (r[2] == 1) winFailures[w]++;
            if (!winMeasured[w]) continue; // warm-up exclu des distributions
            long latency = r[1] - r[0];
            latencies.merge(latency, 1L, Long::sum);
            (r[2] == 1 ? ko : ok).merge(LatencyHistogram.bucketOf(latency), 1L, Long::sum);
            sum += latency;
            measuredTotal++;
        }
        var windows = new ArrayList<LoadWindow>(n);
        for (int i = 0; i < n; i++) {
            long start = firstWindow + i * windowMs;
            windows.add(new LoadWindow(Instant.ofEpochMilli(start), window,
                    planned.inWindow(start, windowMs), winUsers[i], winRequests[i], winFailures[i],
                    winMeasured[i]));
        }
        var histogram = new LatencyHistogram(ok, ko);

        long durationMs = last - first;
        double throughput = total * 1000.0 / durationMs;
        long successful = total - failed;
        boolean hasMeasured = measuredTotal > 0;
        double mean = hasMeasured ? sum / measuredTotal : 0;
        long p50 = hasMeasured ? percentile(latencies, measuredTotal, 50) : 0;
        long p75 = hasMeasured ? percentile(latencies, measuredTotal, 75) : 0;
        long p90 = hasMeasured ? percentile(latencies, measuredTotal, 90) : 0;
        long p95 = hasMeasured ? percentile(latencies, measuredTotal, 95) : 0;
        long p99 = hasMeasured ? percentile(latencies, measuredTotal, 99) : 0;
        long minLatency = hasMeasured ? latencies.firstKey() : 0;
        long maxLatency = hasMeasured ? latencies.lastKey() : 0;
        Map<String, Object> rawStats = Map.of(
                "numberOfRequests", Map.of("total", total, "ok", successful, "ko", failed),
                "meanNumberOfRequestsPerSecond", Map.of("total", throughput),
                "minResponseTime", Map.of("total", minLatency),
                "maxResponseTime", Map.of("total", maxLatency),
                "meanResponseTime", Map.of("total", mean),
                "percentiles1", Map.of("total", p50),
                "percentiles2", Map.of("total", p75),
                "percentiles3", Map.of("total", p95),
                "percentiles4", Map.of("total", p99));
        return new InjectionResult(taskId, simulationClass, Duration.ofMillis(durationMs),
                total, successful, failed, InjectionResult.computeErrorRate(total, failed),
                throughput, p50, p75, p90, p95, p99, maxLatency, minLatency,
                mean, directory.toAbsolutePath().normalize(), rawStats,
                Instant.ofEpochMilli(first), Instant.ofEpochMilli(last), histogram, windows,
                null, true);
    }

    /** {@code measured} de l'étape couvrant l'offset ; au-delà du plan : dernière étape ; sans plan : vrai. */
    private static boolean measuredAt(List<Stage> stages, long[] ends, long offsetMs) {
        if (stages.isEmpty()) return true;
        for (int i = 0; i < ends.length; i++) {
            if (offsetMs < ends[i]) return stages.get(i).measured();
        }
        return stages.get(stages.size() - 1).measured();
    }

    private static long timestamp(String value) {
        long timestamp = Long.parseLong(value);
        if (timestamp < 0) throw new IllegalArgumentException("negative timestamp");
        return timestamp;
    }

    private static long percentile(TreeMap<Long, Long> histogram, long total, int percentile) {
        long rank = (long) Math.ceil(total * (percentile / 100.0));
        long count = 0;
        for (var entry : histogram.entrySet()) {
            count += entry.getValue();
            if (count >= rank) return entry.getKey();
        }
        throw new IllegalStateException("Empty latency histogram");
    }
}

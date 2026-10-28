package com.performance.platform.injection.gatling.result;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads Gatling 3.11's tab-separated RUN, USER and REQUEST records (LogFileDataWriter).
 * A latency histogram keeps memory proportional to distinct millisecond latencies, not requests.
 */
final class SimulationLogResultParser {

    InjectionResult parse(Path directory, TaskId taskId) {
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
                    }
                    case "USER" -> {
                        if (fields.length != 4
                                || !(fields[2].equals("START") || fields[2].equals("END"))) {
                            throw new IllegalArgumentException("invalid USER record");
                        }
                        long timestamp = timestamp(fields[3]);
                        first = Math.min(first, timestamp);
                        last = Math.max(last, timestamp);
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
                        long latency = end - start;
                        latencies.merge(latency, 1L, Long::sum);
                        total++;
                        if (fields[5].equals("KO")) failed++;
                        sum += latency;
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
        long durationMs = last - first;
        double throughput = total * 1000.0 / durationMs;
        long successful = total - failed;
        double mean = sum / total;
        long p50 = percentile(latencies, total, 50);
        long p75 = percentile(latencies, total, 75);
        long p90 = percentile(latencies, total, 90);
        long p95 = percentile(latencies, total, 95);
        long p99 = percentile(latencies, total, 99);
        Map<String, Object> rawStats = Map.of(
                "numberOfRequests", Map.of("total", total, "ok", successful, "ko", failed),
                "meanNumberOfRequestsPerSecond", Map.of("total", throughput),
                "minResponseTime", Map.of("total", latencies.firstKey()),
                "maxResponseTime", Map.of("total", latencies.lastKey()),
                "meanResponseTime", Map.of("total", mean),
                "percentiles1", Map.of("total", p50),
                "percentiles2", Map.of("total", p75),
                "percentiles3", Map.of("total", p95),
                "percentiles4", Map.of("total", p99));
        return new InjectionResult(taskId, simulationClass, Duration.ofMillis(durationMs),
                total, successful, failed, InjectionResult.computeErrorRate(total, failed),
                throughput, p50, p75, p90, p95, p99, latencies.lastKey(), latencies.firstKey(),
                mean, directory.toAbsolutePath().normalize(), rawStats);
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

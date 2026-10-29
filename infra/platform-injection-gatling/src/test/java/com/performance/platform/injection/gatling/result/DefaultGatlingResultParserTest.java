package com.performance.platform.injection.gatling.result;

import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.InjectionResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

@DisplayName("DefaultGatlingResultParser")
class DefaultGatlingResultParserTest {

    private final DefaultGatlingResultParser parser = new DefaultGatlingResultParser();

    /**
     * Fixture Gatling 3.x stats.json (format tableau "stats").
     */
    private static final String STATS_JSON_CONTENT = """
            {
              "stats": [
                {
                  "name": "Global Information",
                  "numberOfRequests": {
                    "total": 1000,
                    "ok": 985,
                    "ko": 15
                  },
                  "minResponseTime": { "total": 5 },
                  "maxResponseTime": { "total": 450 },
                  "meanResponseTime": { "total": 45.2 },
                  "standardDeviation": { "total": 25.1 },
                  "percentiles1": { "total": 22 },
                  "percentiles2": { "total": 38 },
                  "percentiles3": { "total": 75 },
                  "percentiles4": { "total": 120 },
                  "meanNumberOfRequestsPerSecond": { "total": 50.5 }
                },
                {
                  "name": "request_0",
                  "numberOfRequests": {
                    "total": 1000,
                    "ok": 985,
                    "ko": 15
                  },
                  "minResponseTime": { "total": 5 },
                  "maxResponseTime": { "total": 450 },
                  "meanResponseTime": { "total": 45.2 },
                  "standardDeviation": { "total": 25.1 },
                  "percentiles1": { "total": 22 },
                  "percentiles2": { "total": 38 },
                  "percentiles3": { "total": 75 },
                  "percentiles4": { "total": 120 },
                  "meanNumberOfRequestsPerSecond": { "total": 50.5 }
                }
              ]
            }""";

    /**
     * Fixture Gatling 3.x stats.json (format map "contents").
     */
    private static final String CONTENTS_JSON_CONTENT = """
            {
              "contents": {
                "Global": {
                  "numberOfRequests": {
                    "total": 500,
                    "ok": 480,
                    "ko": 20
                  },
                  "minResponseTime": { "total": 2 },
                  "maxResponseTime": { "total": 300 },
                  "meanResponseTime": { "total": 30.0 },
                  "standardDeviation": { "total": 15.0 },
                  "percentiles1": { "total": 15 },
                  "percentiles2": { "total": 25 },
                  "percentiles3": { "total": 60 },
                  "percentiles4": { "total": 90 },
                  "meanNumberOfRequestsPerSecond": { "total": 25.0 }
                }
              }
            }""";

    // === Tests: format "stats" array ===

    @Test
    @DisplayName("should parse stats.json with stats array format")
    void shouldParseStatsArrayFormat(@TempDir Path tempDir) throws IOException {
        Path statsFile = tempDir.resolve("stats.json");
        Files.writeString(statsFile, STATS_JSON_CONTENT);

        var taskId = TaskId.of("task-1");
        InjectionResult result = parser.parse(tempDir, taskId);

        assertThat(result.taskId()).isEqualTo(taskId);
        assertThat(result.totalRequests()).isEqualTo(1000);
        assertThat(result.successfulRequests()).isEqualTo(985);
        assertThat(result.failedRequests()).isEqualTo(15);
        assertThat(result.errorRate()).isCloseTo(1.5, offset(0.015));
        assertThat(result.throughput()).isCloseTo(50.5, offset(0.505));
        assertThat(result.minMs()).isEqualTo(5);
        assertThat(result.maxMs()).isEqualTo(450);
        assertThat(result.meanMs()).isCloseTo(45.2, offset(0.452));
        assertThat(result.p50Ms()).isEqualTo(22);
        assertThat(result.p75Ms()).isEqualTo(38);
        assertThat(result.p90Ms()).isEqualTo(66);    // interpole p75-p95
        assertThat(result.p95Ms()).isEqualTo(75);
        assertThat(result.p99Ms()).isEqualTo(120);
        assertThat(result.gatlingReportDirectory()).isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(result.rawStats()).isNotEmpty();
    }

    // === Tests: format "contents" map ===

    @Test
    @DisplayName("should parse stats.json with contents map format")
    void shouldParseContentsMapFormat(@TempDir Path tempDir) throws IOException {
        Path statsFile = tempDir.resolve("stats.json");
        Files.writeString(statsFile, CONTENTS_JSON_CONTENT);

        var taskId = TaskId.of("task-2");
        InjectionResult result = parser.parse(tempDir, taskId);

        assertThat(result.totalRequests()).isEqualTo(500);
        assertThat(result.successfulRequests()).isEqualTo(480);
        assertThat(result.failedRequests()).isEqualTo(20);
        assertThat(result.errorRate()).isCloseTo(4.0, offset(0.04));
        assertThat(result.throughput()).isCloseTo(25.0, offset(0.25));
        assertThat(result.p50Ms()).isEqualTo(15);
        assertThat(result.p75Ms()).isEqualTo(25);
        assertThat(result.p90Ms()).isEqualTo(51);    // interpole p75-p95
        assertThat(result.p95Ms()).isEqualTo(60);
        assertThat(result.p99Ms()).isEqualTo(90);
    }

    // === Tests: js/stats.json path ===

    @Test
    @DisplayName("should find stats.json in js/ subdirectory")
    void shouldFindStatsInJsSubdirectory(@TempDir Path tempDir) throws IOException {
        Path jsDir = tempDir.resolve("js");
        Files.createDirectory(jsDir);
        Files.writeString(jsDir.resolve("stats.json"), STATS_JSON_CONTENT);

        var taskId = TaskId.of("task-3");
        InjectionResult result = parser.parse(tempDir, taskId);

        assertThat(result.totalRequests()).isEqualTo(1000);
    }

    // === Tests: error cases ===

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("should throw ResultParsingException when stats.json is missing")
        void shouldThrowWhenStatsJsonMissing(@TempDir Path tempDir) {
            var taskId = TaskId.of("task-missing");

            assertThatThrownBy(() -> parser.parse(tempDir, taskId))
                    .isInstanceOf(ResultParsingException.class)
                    .hasMessageContaining("stats.json not found");
        }

        @Test
        @DisplayName("should throw ResultParsingException when JSON is malformed")
        void shouldThrowWhenJsonIsMalformed(@TempDir Path tempDir) throws IOException {
            Files.writeString(tempDir.resolve("stats.json"), "{not valid json");

            var taskId = TaskId.of("task-malformed");

            assertThatThrownBy(() -> parser.parse(tempDir, taskId))
                    .isInstanceOf(ResultParsingException.class)
                    .hasMessageContaining("Failed to read");
        }

        @Test
        @DisplayName("should throw ResultParsingException when required field is missing")
        void shouldThrowWhenRequiredFieldMissing(@TempDir Path tempDir) throws IOException {
            Files.writeString(tempDir.resolve("stats.json"),
                    "{\"stats\":[{\"numberOfRequests\":{\"ok\":1,\"ko\":0}}]}");

            var taskId = TaskId.of("task-missing-field");

            assertThatThrownBy(() -> parser.parse(tempDir, taskId))
                    .isInstanceOf(ResultParsingException.class)
                    .hasMessageContaining("total");
        }

        @Test
        @DisplayName("should throw ResultParsingException on unrecognized format")
        void shouldThrowOnUnrecognizedFormat(@TempDir Path tempDir) throws IOException {
            Files.writeString(tempDir.resolve("stats.json"),
                    "{\"unknown\":{}}");

            var taskId = TaskId.of("task-unknown-format");

            assertThatThrownBy(() -> parser.parse(tempDir, taskId))
                    .isInstanceOf(ResultParsingException.class)
                    .hasMessageContaining("Unrecognized");
        }
    }

    // === Tests: edge cases ===

    @Test
    @DisplayName("should compute 0% error rate when all requests succeed")
    void shouldComputeZeroErrorRate(@TempDir Path tempDir) throws IOException {
        String json = """
                {
                  "stats": [{
                    "numberOfRequests": {"total": 100, "ok": 100, "ko": 0},
                    "minResponseTime": {"total": 1},
                    "maxResponseTime": {"total": 10},
                    "meanResponseTime": {"total": 5.0},
                    "percentiles1": {"total": 3},
                    "percentiles2": {"total": 5},
                    "percentiles3": {"total": 8},
                    "percentiles4": {"total": 10},
                    "meanNumberOfRequestsPerSecond": {"total": 10.0}
                  }]
                }""";
        Files.writeString(tempDir.resolve("stats.json"), json);

        InjectionResult result = parser.parse(tempDir, TaskId.of("all-ok"));

        assertThat(result.errorRate()).isEqualTo(0.0);
        assertThat(result.failedRequests()).isEqualTo(0);
    }

    @Test
    @DisplayName("should compute 100% error rate when all requests fail")
    void shouldComputeFullErrorRate(@TempDir Path tempDir) throws IOException {
        String json = """
                {
                  "stats": [{
                    "numberOfRequests": {"total": 50, "ok": 0, "ko": 50},
                    "minResponseTime": {"total": 1},
                    "maxResponseTime": {"total": 10},
                    "meanResponseTime": {"total": 5.0},
                    "percentiles1": {"total": 3},
                    "percentiles2": {"total": 5},
                    "percentiles3": {"total": 8},
                    "percentiles4": {"total": 10},
                    "meanNumberOfRequestsPerSecond": {"total": 5.0}
                  }]
                }""";
        Files.writeString(tempDir.resolve("stats.json"), json);

        InjectionResult result = parser.parse(tempDir, TaskId.of("all-ko"));

        assertThat(result.errorRate()).isEqualTo(100.0);
    }
}

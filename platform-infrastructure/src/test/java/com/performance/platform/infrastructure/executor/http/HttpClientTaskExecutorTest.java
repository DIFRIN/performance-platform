package com.performance.platform.infrastructure.executor.http;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HttpClientTaskExecutor")
@WireMockTest(httpPort = 0)
class HttpClientTaskExecutorTest {

    private com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo wmRuntimeInfo;

    private HttpTargetRegistry targetRegistry;
    private HttpClientTaskExecutor executor;

    @BeforeEach
    void setUp(com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo wmRuntimeInfo) {
        this.wmRuntimeInfo = wmRuntimeInfo;
        String baseUrl = "http://localhost:" + wmRuntimeInfo.getHttpPort();
        HttpTargetProperties props = new HttpTargetProperties(
                baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(5),
                Map.of(),
                Map.of("health", "/actuator/health",
                       "reset", "/__admin/requests",
                       "devices", "/api/devices"));
        RestClient.Builder builder = RestClient.builder();
        targetRegistry = new HttpTargetRegistry(Map.of("test-target", props), builder);
        executor = new HttpClientTaskExecutor(targetRegistry);
    }

    @AfterEach
    void tearDown() {
        // @WireMockTest manages server lifecycle automatically
    }



    private static ExecutionContext emptyContext() {
        return ExecutionContext.initial(
                ExecutionId.of("exec-001"), ScenarioId.of("scenario-001"));
    }

    private static StepDefinition step(String taskName, Map<String, Object> params) {
        return new StepDefinition(
                TaskId.of("task-001"), taskName, Phase.PREPARATION,
                params, null, null, null, null);
    }

    // ========================================================================
    // Constructor
    // ========================================================================

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects null targetRegistry")
        void shouldRejectNullRegistry() {
            assertThatThrownBy(() -> new HttpClientTaskExecutor(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("targetRegistry");
        }

        @Test
        @DisplayName("getSupportedTaskName returns http-client")
        void shouldReturnCorrectTaskName() {
            assertThat(executor.getSupportedTaskName()).isEqualTo("http-client");
        }
    }

    // ========================================================================
    // Parameter validation
    // ========================================================================

    @Nested
    @DisplayName("parameter validation")
    class ParameterValidationTests {

        @Test
        @DisplayName("fails when target is missing")
        void shouldFailWhenTargetMissing() {
            var s = step("http-client", Map.of("path", "/health"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("target");
        }

        @Test
        @DisplayName("fails when target is blank")
        void shouldFailWhenTargetBlank() {
            var s = step("http-client", Map.of("target", "  ", "path", "/health"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("target");
        }

        @Test
        @DisplayName("fails when path is missing")
        void shouldFailWhenPathMissing() {
            var s = step("http-client", Map.of("target", "test-target"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("path");
        }

        @Test
        @DisplayName("fails when path is blank")
        void shouldFailWhenPathBlank() {
            var s = step("http-client", Map.of("target", "test-target", "path", ""));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("path");
        }

        @Test
        @DisplayName("fails when target is unknown")
        void shouldFailWhenTargetUnknown() {
            var s = step("http-client", Map.of("target", "nonexistent", "path", "/health"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unknown http-target");
            assertThat(result.errorMessage()).contains("nonexistent");
        }

        @Test
        @DisplayName("rejects null context")
        void shouldRejectNullContext() {
            var s = step("http-client", Map.of("target", "test-target", "path", "/health"));

            assertThatThrownBy(() -> executor.execute(null, s))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null step")
        void shouldRejectNullStep() {
            assertThatThrownBy(() -> executor.execute(emptyContext(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ========================================================================
    // GET requests
    // ========================================================================

    @Nested
    @DisplayName("GET requests")
    class GetRequestTests {

        @Test
        @DisplayName("succeeds with 200 and returns outputs")
        void shouldSucceedWith200() {
            stubFor(get(urlEqualTo("/actuator/health"))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader("Content-Type", "text/plain")
                            .withBody("UP")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "path", "/actuator/health"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs()).containsKeys(
                    HttpClientTaskExecutor.OUTPUT_STATUS_CODE,
                    HttpClientTaskExecutor.OUTPUT_RESPONSE_BODY,
                    HttpClientTaskExecutor.OUTPUT_DURATION_MS);
            assertThat(result.outputs().get(HttpClientTaskExecutor.OUTPUT_STATUS_CODE))
                    .isEqualTo(200);
            assertThat(result.outputs().get(HttpClientTaskExecutor.OUTPUT_RESPONSE_BODY))
                    .isEqualTo("UP");
        }

        @Test
        @DisplayName("defaults method to GET when not specified")
        void shouldDefaultToGet() {
            stubFor(get(urlEqualTo("/actuator/health"))
                    .willReturn(aResponse().withStatus(200).withBody("OK")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "path", "/actuator/health"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        }
    }

    // ========================================================================
    // POST requests
    // ========================================================================

    @Nested
    @DisplayName("POST requests")
    class PostRequestTests {

        @Test
        @DisplayName("succeeds with body")
        void shouldPostWithBody() {
            stubFor(post(urlEqualTo("/api/devices"))
                    .withRequestBody(equalToJson("{\"name\":\"sensor-1\"}"))
                    .willReturn(aResponse().withStatus(201)
                            .withBody("{\"id\":42}")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "method", "POST",
                    "path", "/api/devices",
                    "body", "{\"name\":\"sensor-1\"}"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs().get(HttpClientTaskExecutor.OUTPUT_STATUS_CODE))
                    .isEqualTo(201);
            assertThat(result.outputs().get(HttpClientTaskExecutor.OUTPUT_RESPONSE_BODY))
                    .isEqualTo("{\"id\":42}");
        }
    }

    // ========================================================================
    // PUT requests
    // ========================================================================

    @Nested
    @DisplayName("PUT requests")
    class PutRequestTests {

        @Test
        @DisplayName("succeeds with 200")
        void shouldPutWithBody() {
            stubFor(put(urlEqualTo("/api/devices/42"))
                    .willReturn(aResponse().withStatus(200)
                            .withBody("{\"id\":42,\"name\":\"updated\"}")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "method", "PUT",
                    "path", "/api/devices/42",
                    "body", "{\"name\":\"updated\"}"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs().get(HttpClientTaskExecutor.OUTPUT_STATUS_CODE))
                    .isEqualTo(200);
        }
    }

    // ========================================================================
    // DELETE requests
    // ========================================================================

    @Nested
    @DisplayName("DELETE requests")
    class DeleteRequestTests {

        @Test
        @DisplayName("succeeds with 204")
        void shouldDeleteAndReturn204() {
            stubFor(delete(urlEqualTo("/api/devices/42"))
                    .willReturn(aResponse().withStatus(204)));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "method", "DELETE",
                    "path", "/api/devices/42"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs().get(HttpClientTaskExecutor.OUTPUT_STATUS_CODE))
                    .isEqualTo(204);
        }
    }

    // ========================================================================
    // PATCH requests
    // ========================================================================

    @Nested
    @DisplayName("PATCH requests")
    class PatchRequestTests {

        @Test
        @DisplayName("succeeds with body")
        void shouldPatchWithBody() {
            stubFor(patch(urlEqualTo("/api/devices/42"))
                    .willReturn(aResponse().withStatus(200)
                            .withBody("{\"id\":42,\"status\":\"active\"}")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "method", "PATCH",
                    "path", "/api/devices/42",
                    "body", "{\"status\":\"active\"}"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        }
    }

    // ========================================================================
    // expectedStatus assertion
    // ========================================================================

    @Nested
    @DisplayName("expectedStatus assertion")
    class ExpectedStatusTests {

        @Test
        @DisplayName("fails when expectedStatus 200 but got 404")
        void shouldFailWhenStatusMismatch() {
            stubFor(get(urlEqualTo("/health"))
                    .willReturn(aResponse().withStatus(404)
                            .withBody("Not Found")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "path", "/health",
                    "expectedStatus", 200));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Expected HTTP 200 but got 404");
        }

        @Test
        @DisplayName("succeeds when expectedStatus 0 and response is 404")
        void shouldSucceedWithNoStatusAssertion() {
            stubFor(get(urlEqualTo("/health"))
                    .willReturn(aResponse().withStatus(404)
                            .withBody("Not Found")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "path", "/health",
                    "expectedStatus", 0));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs().get(HttpClientTaskExecutor.OUTPUT_STATUS_CODE))
                    .isEqualTo(404);
        }

        @Test
        @DisplayName("succeeds when expectedStatus not set (default 0) and response 404")
        void shouldSucceedWithDefaultExpectedStatus() {
            stubFor(get(urlEqualTo("/health"))
                    .willReturn(aResponse().withStatus(404)
                            .withBody("Not Found")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "path", "/health"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        }

        @Test
        @DisplayName("parses expectedStatus from String value")
        void shouldParseExpectedStatusFromString() {
            stubFor(get(urlEqualTo("/health"))
                    .willReturn(aResponse().withStatus(200).withBody("OK")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "path", "/health",
                    "expectedStatus", "200"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        }
    }

    // ========================================================================
    // Path resolution
    // ========================================================================

    @Nested
    @DisplayName("path resolution")
    class PathResolutionTests {

        @Test
        @DisplayName("resolves logical path via resolvePath")
        void shouldResolveLogicalPath() {
            stubFor(get(urlEqualTo("/actuator/health"))
                    .willReturn(aResponse().withStatus(200).withBody("UP")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "path", "health"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs().get(HttpClientTaskExecutor.OUTPUT_STATUS_CODE))
                    .isEqualTo(200);
        }

        @Test
        @DisplayName("passes absolute path through")
        void shouldPassAbsolutePath() {
            stubFor(get(urlEqualTo("/custom/path"))
                    .willReturn(aResponse().withStatus(200).withBody("OK")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "path", "/custom/path"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
        }
    }

    // ========================================================================
    // Server errors
    // ========================================================================

    @Nested
    @DisplayName("server errors")
    class ServerErrorTests {

        @Test
        @DisplayName("handles 500 response as success when no expectedStatus")
        void shouldHandle500AsSuccess() {
            stubFor(get(urlEqualTo("/error"))
                    .willReturn(aResponse().withStatus(500)
                            .withBody("Internal Error")));

            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "path", "/error"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.SUCCESS);
            assertThat(result.outputs().get(HttpClientTaskExecutor.OUTPUT_STATUS_CODE))
                    .isEqualTo(500);
        }
    }

    // ========================================================================
    // Unsupported method
    // ========================================================================

    @Nested
    @DisplayName("unsupported method")
    class UnsupportedMethodTests {

        @Test
        @DisplayName("fails with unsupported HTTP method")
        void shouldFailWithUnsupportedMethod() {
            var s = step("http-client", Map.of(
                    "target", "test-target",
                    "method", "OPTIONS",
                    "path", "/health"));

            TaskResult result = executor.execute(emptyContext(), s);

            assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.errorMessage()).contains("Unsupported HTTP method");
        }
    }
}

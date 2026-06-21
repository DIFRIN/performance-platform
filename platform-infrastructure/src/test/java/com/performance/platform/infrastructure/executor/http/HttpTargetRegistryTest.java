package com.performance.platform.infrastructure.executor.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HttpTargetRegistry")
class HttpTargetRegistryTest {

    private static final HttpTargetProperties WIREMOCK_PROPS = new HttpTargetProperties(
            "http://wiremock:8080",
            Duration.ofSeconds(2),
            Duration.ofSeconds(5),
            Map.of("X-Custom", "value"),
            Map.of("reset-requests", "/__admin/requests",
                   "count-requests", "/__admin/requests/count",
                   "health", "/__admin/health")
    );

    private static final HttpTargetProperties DEVICE_API_PROPS = new HttpTargetProperties(
            "http://device-api:8082",
            Duration.ofSeconds(2),
            Duration.ofSeconds(10),
            Map.of(),
            Map.of("submit-event", "/api/events",
                   "check-device", "/api/devices")
    );

    private final RestClient.Builder restClientBuilder = RestClient.builder();

    private final HttpTargetRegistry registry = new HttpTargetRegistry(Map.of(
            "wiremock", WIREMOCK_PROPS,
            "device-api", DEVICE_API_PROPS
    ), restClientBuilder);

    @Nested
    @DisplayName("get()")
    class GetTests {

        @Test
        @DisplayName("returns target properties for known name")
        void shouldReturnTargetForKnownName() {
            assertThat(registry.get("wiremock")).isSameAs(WIREMOCK_PROPS);
            assertThat(registry.get("device-api")).isSameAs(DEVICE_API_PROPS);
        }

        @Test
        @DisplayName("returns null for unknown target name")
        void shouldReturnNullForUnknownTarget() {
            assertThat(registry.get("unknown")).isNull();
        }
    }

    @Nested
    @DisplayName("resolvePath()")
    class ResolvePathTests {

        @Test
        @DisplayName("resolves logical path to real path when mapped")
        void shouldResolvePathWithMapping() {
            assertThat(registry.resolvePath("wiremock", "reset-requests"))
                    .isEqualTo("/__admin/requests");
            assertThat(registry.resolvePath("wiremock", "count-requests"))
                    .isEqualTo("/__admin/requests/count");
            assertThat(registry.resolvePath("wiremock", "health"))
                    .isEqualTo("/__admin/health");
        }

        @Test
        @DisplayName("falls back to logicalPath when no mapping exists")
        void shouldFallbackWhenNoMapping() {
            assertThat(registry.resolvePath("wiremock", "/direct"))
                    .isEqualTo("/direct");
        }

        @Test
        @DisplayName("falls back to logicalPath for unknown target")
        void shouldFallbackForUnknownTarget() {
            assertThat(registry.resolvePath("unknown", "/anything"))
                    .isEqualTo("/anything");
        }

        @Test
        @DisplayName("falls back for target with empty paths")
        void shouldFallbackForEmptyPaths() {
            assertThat(registry.resolvePath("device-api", "submit-event"))
                    .isEqualTo("/api/events");
        }
    }

    @Nested
    @DisplayName("clientFor()")
    class ClientForTests {

        @Test
        @DisplayName("creates RestClient for known target")
        void shouldCreateClientForKnownTarget() {
            RestClient client = registry.clientFor("wiremock");
            assertThat(client).isNotNull();
        }

        @Test
        @DisplayName("returns same instance on second call (lazy cache)")
        void shouldReturnCachedClientOnSecondCall() {
            RestClient client1 = registry.clientFor("device-api");
            RestClient client2 = registry.clientFor("device-api");
            assertThat(client1).isSameAs(client2);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for unknown target")
        void shouldThrowForUnknownTarget() {
            assertThatThrownBy(() -> registry.clientFor("unknown"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown http-target")
                    .hasMessageContaining("unknown");
        }
    }

    @Nested
    @DisplayName("immutability and defaults")
    class ImmutabilityTests {

        @Test
        @DisplayName("HttpTargetProperties defaults connectionTimeout to 2s when null")
        void shouldDefaultConnectionTimeout() {
            var props = new HttpTargetProperties("http://localhost", null, null, null, null);
            assertThat(props.connectionTimeout()).isEqualTo(Duration.ofSeconds(2));
        }

        @Test
        @DisplayName("HttpTargetProperties defaults readTimeout to 5s when null")
        void shouldDefaultReadTimeout() {
            var props = new HttpTargetProperties("http://localhost", Duration.ofSeconds(3), null, null, null);
            assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("HttpTargetProperties defaults defaultHeaders to empty map when null")
        void shouldDefaultHeadersToEmptyMap() {
            var props = new HttpTargetProperties("http://localhost", null, null, null, null);
            assertThat(props.defaultHeaders()).isEmpty();
        }

        @Test
        @DisplayName("HttpTargetProperties defaults paths to empty map when null")
        void shouldDefaultPathsToEmptyMap() {
            var props = new HttpTargetProperties("http://localhost", null, null, null, null);
            assertThat(props.paths()).isEmpty();
        }

        @Test
        @DisplayName("HttpTargetProperties rejects null baseUrl")
        void shouldRejectNullBaseUrl() {
            assertThatThrownBy(() -> new HttpTargetProperties(null, null, null, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("baseUrl");
        }

        @Test
        @DisplayName("Registry constructor defensively copies the input map")
        void shouldDefensivelyCopyInputMap() {
            var mutableMap = new HashMap<>(Map.of(
                    "wiremock", WIREMOCK_PROPS
            ));
            var reg = new HttpTargetRegistry(mutableMap, restClientBuilder);
            mutableMap.clear();
            assertThat(reg.get("wiremock")).isNotNull();
        }

        @Test
        @DisplayName("PlatformHttpTargetsProperties defaults httpTargets to empty map when null")
        void shouldDefaultHttpTargetsToEmptyMap() {
            var props = new PlatformHttpTargetsProperties(null);
            assertThat(props.httpTargets()).isEmpty();
        }
    }
}

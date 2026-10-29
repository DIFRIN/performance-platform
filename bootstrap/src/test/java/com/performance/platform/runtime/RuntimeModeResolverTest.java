package com.performance.platform.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RuntimeModeResolver")
class RuntimeModeResolverTest {

    private MockEnvironment environment;
    private RuntimeModeResolver resolver;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        resolver = new RuntimeModeResolver(environment);
    }

    // ---- resolveMode ----

    @Nested
    @DisplayName("resolveMode")
    class ResolveModeTests {

        @Test
        @DisplayName("should return LOCAL by default when nothing is set")
        void shouldReturnLocalByDefault() {
            assertThat(resolver.resolveMode()).isEqualTo(RuntimeMode.LOCAL);
        }

        @Test
        @DisplayName("should resolve DISTRIBUTED from property")
        void shouldResolveDistributedFromProperty() {
            environment.setProperty("runtime.mode", "DISTRIBUTED");
            assertThat(resolver.resolveMode()).isEqualTo(RuntimeMode.DISTRIBUTED);
        }

        @Test
        @DisplayName("should resolve LOCAL from property")
        void shouldResolveLocalFromProperty() {
            environment.setProperty("runtime.mode", "LOCAL");
            assertThat(resolver.resolveMode()).isEqualTo(RuntimeMode.LOCAL);
        }

        @Test
        @DisplayName("should be case-insensitive")
        void shouldBeCaseInsensitive() {
            environment.setProperty("runtime.mode", "distributed");
            assertThat(resolver.resolveMode()).isEqualTo(RuntimeMode.DISTRIBUTED);
        }

        @Test
        @DisplayName("should trim whitespace from property value")
        void shouldTrimWhitespace() {
            environment.setProperty("runtime.mode", "  DISTRIBUTED  ");
            assertThat(resolver.resolveMode()).isEqualTo(RuntimeMode.DISTRIBUTED);
        }

        @Test
        @DisplayName("should throw on invalid mode value")
        void shouldThrowOnInvalidMode() {
            environment.setProperty("runtime.mode", "CLUSTER");
            assertThatThrownBy(() -> resolver.resolveMode())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CLUSTER");
        }

        @Test
        @DisplayName("should return LOCAL when property is blank")
        void shouldReturnLocalWhenPropertyBlank() {
            environment.setProperty("runtime.mode", "   ");
            assertThat(resolver.resolveMode()).isEqualTo(RuntimeMode.LOCAL);
        }

        @Test
        @DisplayName("should prioritize RUNTIME_MODE env var over runtime.mode property")
        void shouldPrioritizeEnvVarOverPropertyForMode() {
            environment.withProperty("RUNTIME_MODE", "DISTRIBUTED");
            environment.setProperty("runtime.mode", "LOCAL");
            assertThat(resolver.resolveMode()).isEqualTo(RuntimeMode.DISTRIBUTED);
        }
    }

    // ---- resolveRole ----

    @Nested
    @DisplayName("resolveRole")
    class ResolveRoleTests {

        @Test
        @DisplayName("should return NONE by default")
        void shouldReturnNoneByDefault() {
            assertThat(resolver.resolveRole()).isEqualTo(RuntimeRole.NONE);
        }

        @Test
        @DisplayName("should resolve ORCHESTRATOR from property")
        void shouldResolveOrchestratorFromProperty() {
            environment.setProperty("runtime.role", "ORCHESTRATOR");
            assertThat(resolver.resolveRole()).isEqualTo(RuntimeRole.ORCHESTRATOR);
        }

        @Test
        @DisplayName("should resolve AGENT from property")
        void shouldResolveAgentFromProperty() {
            environment.setProperty("runtime.role", "AGENT");
            assertThat(resolver.resolveRole()).isEqualTo(RuntimeRole.AGENT);
        }

        @Test
        @DisplayName("should be case-insensitive for role")
        void shouldBeCaseInsensitiveForRole() {
            environment.setProperty("runtime.role", "orchestrator");
            assertThat(resolver.resolveRole()).isEqualTo(RuntimeRole.ORCHESTRATOR);
        }

        @Test
        @DisplayName("should throw on invalid role value")
        void shouldThrowOnInvalidRole() {
            environment.setProperty("runtime.role", "WORKER");
            assertThatThrownBy(() -> resolver.resolveRole())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("WORKER");
        }

        @Test
        @DisplayName("should prioritize MODE env var over runtime.role property")
        void shouldPrioritizeEnvVarOverPropertyForRole() {
            environment.withProperty("MODE", "AGENT");
            environment.setProperty("runtime.role", "ORCHESTRATOR");
            assertThat(resolver.resolveRole()).isEqualTo(RuntimeRole.AGENT);
        }
    }

    // ---- resolveTransportType ----

    @Nested
    @DisplayName("resolveTransportType")
    class ResolveTransportTypeTests {

        @Test
        @DisplayName("should return null by default")
        void shouldReturnNullByDefault() {
            assertThat(resolver.resolveTransportType()).isNull();
        }

        @Test
        @DisplayName("should resolve transport from property")
        void shouldResolveFromProperty() {
            environment.setProperty("transport.type", "KAFKA");
            assertThat(resolver.resolveTransportType()).isEqualTo("KAFKA");
        }

        @Test
        @DisplayName("should trim whitespace from transport value")
        void shouldTrimTransportWhitespace() {
            environment.setProperty("transport.type", "  KAFKA  ");
            assertThat(resolver.resolveTransportType()).isEqualTo("KAFKA");
        }

        @Test
        @DisplayName("should prioritize TRANSPORT_TYPE env var over transport.type property")
        void shouldPrioritizeEnvVarOverPropertyForTransport() {
            environment.withProperty("TRANSPORT_TYPE", "KAFKA");
            environment.setProperty("transport.type", "HTTP");
            assertThat(resolver.resolveTransportType()).isEqualTo("KAFKA");
        }
    }

    // ---- parse helpers ----

    @Nested
    @DisplayName("parse helpers")
    class ParseHelperTests {

        @Test
        @DisplayName("should parse LOCAL")
        void shouldParseLocal() {
            assertThat(RuntimeModeResolver.parseMode("LOCAL"))
                    .isEqualTo(RuntimeMode.LOCAL);
        }

        @Test
        @DisplayName("should parse DISTRIBUTED")
        void shouldParseDistributed() {
            assertThat(RuntimeModeResolver.parseMode("DISTRIBUTED"))
                    .isEqualTo(RuntimeMode.DISTRIBUTED);
        }

        @Test
        @DisplayName("should throw on unknown mode")
        void shouldThrowOnUnknownMode() {
            assertThatThrownBy(() -> RuntimeModeResolver.parseMode("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should parse ORCHESTRATOR role")
        void shouldParseOrchestrator() {
            assertThat(RuntimeModeResolver.parseRole("ORCHESTRATOR"))
                    .isEqualTo(RuntimeRole.ORCHESTRATOR);
        }

        @Test
        @DisplayName("should throw on unknown role")
        void shouldThrowOnUnknownRole() {
            assertThatThrownBy(() -> RuntimeModeResolver.parseRole("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- Constructor ----

    @Test
    @DisplayName("should throw NPE on null environment")
    void shouldThrowOnNullEnvironment() {
        assertThatThrownBy(() -> new RuntimeModeResolver(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- @Component present ----

    @Test
    @DisplayName("should be annotated with @Component")
    void shouldBeAnnotatedWithComponent() {
        var annotation = RuntimeModeResolver.class.getAnnotation(
                org.springframework.stereotype.Component.class);
        assertThat(annotation).isNotNull();
    }

    // ---- Constants ----

    @Test
    @DisplayName("should have correct env var constant names")
    void shouldHaveCorrectEnvConstants() {
        assertThat(RuntimeModeResolver.ENV_RUNTIME_MODE).isEqualTo("RUNTIME_MODE");
        assertThat(RuntimeModeResolver.ENV_MODE).isEqualTo("MODE");
        assertThat(RuntimeModeResolver.ENV_TRANSPORT_TYPE).isEqualTo("TRANSPORT_TYPE");
    }

    @Test
    @DisplayName("should have correct property constant names")
    void shouldHaveCorrectPropertyConstants() {
        assertThat(RuntimeModeResolver.PROP_RUNTIME_MODE).isEqualTo("runtime.mode");
        assertThat(RuntimeModeResolver.PROP_RUNTIME_ROLE).isEqualTo("runtime.role");
        assertThat(RuntimeModeResolver.PROP_TRANSPORT_TYPE).isEqualTo("transport.type");
    }
}

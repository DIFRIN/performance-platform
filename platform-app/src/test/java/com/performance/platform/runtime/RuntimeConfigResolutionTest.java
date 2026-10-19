package com.performance.platform.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T3 — resolution unique mode/role/transport.
 * Priorite : env > CLI > property > defaut.
 */
@DisplayName("RuntimeConfigResolver (T3)")
class RuntimeConfigResolutionTest {

    private MockEnvironment environment;
    private RuntimeConfigResolver resolver;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        resolver = new RuntimeConfigResolver(environment);
    }

    // ------------------------------------------------------------------
    // Role
    // ------------------------------------------------------------------

    static Stream<Arguments> roleCases() {
        return Stream.of(
                Arguments.of("AGENT", null, "ORCHESTRATOR", RuntimeRole.AGENT),
                Arguments.of(null, "--runtime.role=AGENT", "ORCHESTRATOR", RuntimeRole.AGENT),
                Arguments.of(null, null, "ORCHESTRATOR", RuntimeRole.ORCHESTRATOR),
                Arguments.of(null, null, null, RuntimeRole.NONE),
                Arguments.of(" agent ", null, null, RuntimeRole.AGENT)
        );
    }

    @ParameterizedTest(name = "env={0} cli={1} prop={2} -> {3}")
    @MethodSource("roleCases")
    @DisplayName("should apply env > CLI > property > default")
    void shouldResolveRole(String env, String cli, String property, RuntimeRole expected) {
        if (env != null) {
            environment.setProperty(RuntimeConfigResolver.ENV_MODE, env);
        }
        if (property != null) {
            environment.setProperty(RuntimeConfigResolver.PROP_RUNTIME_ROLE, property);
        }

        assertThat(resolver.resolveRole(args(cli))).isEqualTo(expected);
    }

    @Test
    @DisplayName("should infer AGENT from the Spring agent profile")
    void shouldInferAgentFromProfile() {
        assertThat(resolver.resolveRole(new String[]{"--spring.profiles.active=local,agent"}))
                .isEqualTo(RuntimeRole.AGENT);
    }

    // ------------------------------------------------------------------
    // Mode
    // ------------------------------------------------------------------

    @Test
    @DisplayName("env RUNTIME_MODE wins over CLI and property")
    void envWinsForMode() {
        environment.setProperty(RuntimeConfigResolver.ENV_RUNTIME_MODE, "DISTRIBUTED");
        environment.setProperty(RuntimeConfigResolver.PROP_RUNTIME_MODE, "LOCAL");

        assertThat(resolver.resolveMode(new String[]{"--runtime.mode=LOCAL"}))
                .isEqualTo(RuntimeMode.DISTRIBUTED);
    }

    @Test
    @DisplayName("CLI wins over property for mode")
    void cliWinsForMode() {
        environment.setProperty(RuntimeConfigResolver.PROP_RUNTIME_MODE, "LOCAL");

        assertThat(resolver.resolveMode(new String[]{"--runtime.mode=DISTRIBUTED"}))
                .isEqualTo(RuntimeMode.DISTRIBUTED);
    }

    @Test
    @DisplayName("mode defaults to LOCAL")
    void modeDefaultsToLocal() {
        assertThat(resolver.resolveMode(new String[0])).isEqualTo(RuntimeMode.LOCAL);
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    @Test
    @DisplayName("should apply env > CLI > property for transport")
    void shouldResolveTransport() {
        environment.setProperty(RuntimeConfigResolver.ENV_TRANSPORT_TYPE, "KAFKA");
        environment.setProperty(RuntimeConfigResolver.PROP_TRANSPORT_TYPE, "IN_MEMORY");

        assertThat(resolver.resolveTransportType(new String[]{"--transport.type=RABBITMQ"}))
                .isEqualTo("KAFKA");

        environment = new MockEnvironment();
        environment.setProperty(RuntimeConfigResolver.PROP_TRANSPORT_TYPE, "IN_MEMORY");
        resolver = new RuntimeConfigResolver(environment);

        assertThat(resolver.resolveTransportType(new String[]{"--transport.type=RABBITMQ"}))
                .isEqualTo("RABBITMQ");
    }

    @Test
    @DisplayName("transport defaults to null")
    void transportDefaultsToNull() {
        assertThat(resolver.resolveTransportType(new String[0])).isNull();
    }

    // ------------------------------------------------------------------
    // Full config + errors + bootstrap
    // ------------------------------------------------------------------

    @Test
    @DisplayName("should resolve the full configuration")
    void shouldResolveFullConfig() {
        environment.setProperty(RuntimeConfigResolver.ENV_RUNTIME_MODE, "DISTRIBUTED");
        environment.setProperty(RuntimeConfigResolver.ENV_MODE, "AGENT");
        environment.setProperty(RuntimeConfigResolver.ENV_TRANSPORT_TYPE, "KAFKA");

        assertThat(resolver.resolve(new String[0]))
                .isEqualTo(new RuntimeConfig(
                        RuntimeMode.DISTRIBUTED, RuntimeRole.AGENT, "KAFKA"));
    }

    @Test
    @DisplayName("should reject invalid values")
    void shouldRejectInvalidValues() {
        environment.setProperty(RuntimeConfigResolver.PROP_RUNTIME_MODE, "CLUSTER");
        assertThatThrownBy(() -> resolver.resolveMode(new String[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLUSTER");

        environment = new MockEnvironment();
        environment.setProperty(RuntimeConfigResolver.PROP_RUNTIME_ROLE, "WORKER");
        resolver = new RuntimeConfigResolver(environment);
        assertThatThrownBy(() -> resolver.resolveRole(new String[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WORKER");
    }

    @Test
    @DisplayName("bootstrap detects AGENT from CLI before Spring starts")
    void bootstrapDetectsCliAgent() {
        assertThat(RuntimeConfigResolver.resolveBootstrapRole(
                new String[]{"--runtime.role=AGENT"}, null, null))
                .isEqualTo(RuntimeRole.AGENT);
        assertThat(RuntimeConfigResolver.isAgent(new String[0], "AGENT", null)).isTrue();
        assertThat(RuntimeConfigResolver.isAgent(new String[0], null, null)).isFalse();
    }

    private static String[] args(String cli) {
        return cli == null ? new String[0] : new String[]{cli};
    }
}

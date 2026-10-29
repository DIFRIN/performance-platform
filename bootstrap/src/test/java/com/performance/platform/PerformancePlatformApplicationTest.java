package com.performance.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PerformancePlatformApplication bootstrap")
class PerformancePlatformApplicationTest {

    @Nested
    @DisplayName("detectAgentRole")
    class DetectAgentRoleTests {

        @Test
        @DisplayName("should default to non-agent")
        void shouldDefaultToNonAgent() {
            assertThat(PerformancePlatformApplication.detectAgentRole(null, null, null)).isFalse();
            assertThat(PerformancePlatformApplication.detectAgentRole(new String[0], "", "")).isFalse();
        }

        @Test
        @DisplayName("should detect MODE env var")
        void shouldDetectEnvMode() {
            assertThat(PerformancePlatformApplication.detectAgentRole(null, "AGENT", null)).isTrue();
            assertThat(PerformancePlatformApplication.detectAgentRole(null, " agent ", null)).isTrue();
        }

        @Test
        @DisplayName("should detect -Druntime.role=AGENT")
        void shouldDetectSystemProperty() {
            assertThat(PerformancePlatformApplication.detectAgentRole(null, null, "AGENT")).isTrue();
        }

        @Test
        @DisplayName("should detect --runtime.role=AGENT CLI argument")
        void shouldDetectCliRole() {
            assertThat(PerformancePlatformApplication.detectAgentRole(
                    new String[]{"--runtime.role=AGENT"}, null, null)).isTrue();
        }

        @Test
        @DisplayName("should detect agent Spring profiles")
        void shouldDetectAgentProfile() {
            assertThat(PerformancePlatformApplication.detectAgentRole(
                    new String[]{"--spring.profiles.active=agent"}, null, null)).isTrue();
            assertThat(PerformancePlatformApplication.detectAgentRole(
                    new String[]{"--spring.profiles.active=local,agent"}, null, null)).isTrue();
            assertThat(PerformancePlatformApplication.detectAgentRole(
                    new String[]{"--spring.profiles.active=agent-injector"}, null, null)).isTrue();
            assertThat(PerformancePlatformApplication.detectAgentRole(
                    new String[]{"--spring.profiles.active=agent-asserter"}, null, null)).isTrue();
        }

        @Test
        @DisplayName("should detect an agent profile passed as a system property")
        void shouldDetectAgentProfileSystemProperty() {
            System.setProperty("spring.profiles.active", "agent-injector");
            try {
                assertThat(PerformancePlatformApplication.isAgentMode(new String[0])).isTrue();
            } finally {
                System.clearProperty("spring.profiles.active");
            }
        }

        @Test
        @DisplayName("should ignore non-agent arguments")
        void shouldIgnoreNonAgentArguments() {
            assertThat(PerformancePlatformApplication.detectAgentRole(
                    new String[]{"--spring.profiles.active=local", "--runtime.role=ORCHESTRATOR"},
                    null, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("hasScenarioArg")
    class HasScenarioArgTests {

        @Test
        @DisplayName("should detect --scenario=")
        void shouldDetectScenarioArg() {
            assertThat(PerformancePlatformApplication.hasScenarioArg(
                    new String[]{"--scenario=/tmp/s.yaml"})).isTrue();
        }

        @Test
        @DisplayName("should return false without scenario argument")
        void shouldReturnFalseWithoutScenario() {
            assertThat(PerformancePlatformApplication.hasScenarioArg(
                    new String[]{"--runtime.role=AGENT"})).isFalse();
            assertThat(PerformancePlatformApplication.hasScenarioArg(null)).isFalse();
        }
    }
}

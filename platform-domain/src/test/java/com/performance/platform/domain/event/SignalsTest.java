package com.performance.platform.domain.event;

import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ReportId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour les 7 events agent/assertion/report et l'interface scellee AgentSignal.
 * Couvre : instanciation, egalite par valeur, validation non-null, champs nullables.
 */
@DisplayName("Signals and Agent/Report Events")
class SignalsTest {

    // ─── Helpers ──────────────────────────────────────────────────────────

    private static ExecutionId exec() { return ExecutionId.of("exec-1"); }
    private static TaskId task() { return TaskId.of("task-1"); }
    private static AgentId agent() { return AgentId.of("agent-1"); }
    private static ReportId report() { return new ReportId("report-1"); }
    private static SignalId signal() { return SignalId.generate(); }
    private static Instant now() { return Instant.now(); }

    private static AgentDescriptor sampleDescriptor() {
        return new AgentDescriptor(
                AgentId.of("agent-1"),
                "agent-1",
                "host-1",
                9090,
                "http://host-1:9090/callback",
                Set.of("gatling-http", "db-query"),
                new AgentCapabilities(5, "1.0.0"),
                AgentState.IDLE,
                Instant.now(),
                Instant.now(),
                Duration.ofMinutes(5)
        );
    }

    // ─── AssertionPassed ────────────────────────────────────────────────

    @Nested
    @DisplayName("AssertionPassed")
    class AssertionPassedTest {

        @Test
        @DisplayName("instanciable et champs accessibles")
        void instantiableAndFieldsAccessible() {
            var ts = now();
            var e = new AssertionPassed(exec(), task(), ts);

            assertEquals(exec(), e.executionId());
            assertEquals(task(), e.assertionId());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new AssertionPassed(exec(), task(), ts);
            var e2 = new AssertionPassed(exec(), task(), ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("assertionId different -> pas egaux")
        void differentAssertionIdNotEqual() {
            var ts = now();
            var e1 = new AssertionPassed(exec(), TaskId.of("task-1"), ts);
            var e2 = new AssertionPassed(exec(), TaskId.of("task-2"), ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("executionId null leve NullPointerException")
        void nullExecutionIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    new AssertionPassed(null, task(), now()));
        }

        @Test
        @DisplayName("assertionId null leve NullPointerException")
        void nullAssertionIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    new AssertionPassed(exec(), null, now()));
        }

        @Test
        @DisplayName("timestamp null leve NullPointerException")
        void nullTimestampThrows() {
            assertThrows(NullPointerException.class, () ->
                    new AssertionPassed(exec(), task(), null));
        }
    }

    // ─── AssertionFailed ─────────────────────────────────────────────────

    @Nested
    @DisplayName("AssertionFailed")
    class AssertionFailedTest {

        @Test
        @DisplayName("instanciable et champs accessibles")
        void instantiableAndFieldsAccessible() {
            var ts = now();
            var e = new AssertionFailed(exec(), task(), "expected 200 but got 500", ts);

            assertEquals(exec(), e.executionId());
            assertEquals(task(), e.assertionId());
            assertEquals("expected 200 but got 500", e.reason());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new AssertionFailed(exec(), task(), "reason", ts);
            var e2 = new AssertionFailed(exec(), task(), "reason", ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("reason differente -> pas egaux")
        void differentReasonNotEqual() {
            var ts = now();
            var e1 = new AssertionFailed(exec(), task(), "reason-1", ts);
            var e2 = new AssertionFailed(exec(), task(), "reason-2", ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("executionId null leve NullPointerException")
        void nullExecutionIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    new AssertionFailed(null, task(), "reason", now()));
        }

        @Test
        @DisplayName("reason null leve NullPointerException")
        void nullReasonThrows() {
            assertThrows(NullPointerException.class, () ->
                    new AssertionFailed(exec(), task(), null, now()));
        }
    }

    // ─── AgentRegistered ─────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentRegistered")
    class AgentRegisteredTest {

        @Test
        @DisplayName("instanciable et champs accessibles")
        void instantiableAndFieldsAccessible() {
            var ts = now();
            var desc = sampleDescriptor();
            var e = new AgentRegistered(agent(), desc, ts);

            assertEquals(agent(), e.agentId());
            assertEquals(desc, e.descriptor());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var desc = sampleDescriptor();
            var e1 = new AgentRegistered(agent(), desc, ts);
            var e2 = new AgentRegistered(agent(), desc, ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("descriptor different -> pas egaux")
        void differentDescriptorNotEqual() {
            var ts = now();
            var desc1 = sampleDescriptor();
            var desc2 = new AgentDescriptor(
                    AgentId.of("agent-2"), "agent-2", "host-2", 9091,
                    "http://host-2:9091/callback",
                    Set.of("gatling-http"), new AgentCapabilities(3, "2.0.0"),
                    AgentState.IDLE, Instant.now(), Instant.now(), Duration.ofMinutes(5));
            var e1 = new AgentRegistered(AgentId.of("agent-1"), desc1, ts);
            var e2 = new AgentRegistered(AgentId.of("agent-2"), desc2, ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("descriptor null leve NullPointerException")
        void nullDescriptorThrows() {
            assertThrows(NullPointerException.class, () ->
                    new AgentRegistered(agent(), null, now()));
        }

        @Test
        @DisplayName("agentId null leve NullPointerException")
        void nullAgentIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    new AgentRegistered(null, sampleDescriptor(), now()));
        }
    }

    // ─── AgentLost ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentLost")
    class AgentLostTest {

        @Test
        @DisplayName("instanciable et champs accessibles")
        void instantiableAndFieldsAccessible() {
            var ts = now();
            var e = new AgentLost(agent(), "heartbeat timeout", ts);

            assertEquals(agent(), e.agentId());
            assertEquals("heartbeat timeout", e.reason());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new AgentLost(agent(), "timeout", ts);
            var e2 = new AgentLost(agent(), "timeout", ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("reason null leve NullPointerException")
        void nullReasonThrows() {
            assertThrows(NullPointerException.class, () ->
                    new AgentLost(agent(), null, now()));
        }

        @Test
        @DisplayName("agentId null leve NullPointerException")
        void nullAgentIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    new AgentLost(null, "timeout", now()));
        }
    }

    // ─── AgentRecovered ──────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentRecovered")
    class AgentRecoveredTest {

        @Test
        @DisplayName("instanciable et champs accessibles")
        void instantiableAndFieldsAccessible() {
            var ts = now();
            var e = new AgentRecovered(agent(), ts);

            assertEquals(agent(), e.agentId());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new AgentRecovered(agent(), ts);
            var e2 = new AgentRecovered(agent(), ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("agentId null leve NullPointerException")
        void nullAgentIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    new AgentRecovered(null, now()));
        }

        @Test
        @DisplayName("timestamp null leve NullPointerException")
        void nullTimestampThrows() {
            assertThrows(NullPointerException.class, () ->
                    new AgentRecovered(agent(), null));
        }
    }

    // ─── ReportGenerated ─────────────────────────────────────────────────

    @Nested
    @DisplayName("ReportGenerated")
    class ReportGeneratedTest {

        @Test
        @DisplayName("instanciable et champs accessibles")
        void instantiableAndFieldsAccessible() {
            var ts = now();
            var e = new ReportGenerated(exec(), report(), ts);

            assertEquals(exec(), e.executionId());
            assertEquals(report(), e.reportId());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new ReportGenerated(exec(), report(), ts);
            var e2 = new ReportGenerated(exec(), report(), ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("reportId null leve NullPointerException")
        void nullReportIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ReportGenerated(exec(), null, now()));
        }

        @Test
        @DisplayName("executionId null leve NullPointerException")
        void nullExecutionIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ReportGenerated(null, report(), now()));
        }
    }

    // ─── ReportPublished ─────────────────────────────────────────────────

    @Nested
    @DisplayName("ReportPublished")
    class ReportPublishedTest {

        @Test
        @DisplayName("instanciable avec target String et champs accessibles")
        void instantiableWithStringTarget() {
            var ts = now();
            var e = new ReportPublished(exec(), report(), "CONFLUENCE", ts);

            assertEquals(exec(), e.executionId());
            assertEquals(report(), e.reportId());
            assertEquals("CONFLUENCE", e.target());
            assertEquals(ts, e.timestamp());
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var ts = now();
            var e1 = new ReportPublished(exec(), report(), "S3", ts);
            var e2 = new ReportPublished(exec(), report(), "S3", ts);

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("target different -> pas egaux")
        void differentTargetNotEqual() {
            var ts = now();
            var e1 = new ReportPublished(exec(), report(), "CONFLUENCE", ts);
            var e2 = new ReportPublished(exec(), report(), "S3", ts);

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("target null leve NullPointerException")
        void nullTargetThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ReportPublished(exec(), report(), null, now()));
        }

        @Test
        @DisplayName("reportId null leve NullPointerException")
        void nullReportIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ReportPublished(exec(), null, "CONFLUENCE", now()));
        }
    }

    // ─── AgentSignal sealed interface ────────────────────────────────────

    @Nested
    @DisplayName("AgentSignal sealed interface")
    class AgentSignalTest {

        @Test
        @DisplayName("ScenarioRestartSignal implements AgentSignal")
        void scenarioRestartSignalImplementsAgentSignal() {
            var sig = new ScenarioRestartSignal(signal(), null, "ORCHESTRATOR_RESTART", now());
            assertInstanceOf(AgentSignal.class, sig);
        }

        @Test
        @DisplayName("AgentSignal methods accessible via interface")
        void agentSignalMethodsAccessible() {
            var id = signal();
            var ts = now();
            var sig = new ScenarioRestartSignal(id, null, "RESTART", ts);

            assertEquals(id, sig.id());
            assertEquals(ts, sig.issuedAt());
        }
    }

    // ─── ScenarioRestartSignal ──────────────────────────────────────────

    @Nested
    @DisplayName("ScenarioRestartSignal")
    class ScenarioRestartSignalTest {

        @Test
        @DisplayName("instanciable avec executionId null (broadcast)")
        void instantiableWithNullExecutionId() {
            var id = signal();
            var ts = now();
            var sig = new ScenarioRestartSignal(id, null, "ORCHESTRATOR_RESTART", ts);

            assertEquals(id, sig.id());
            assertNull(sig.executionId());
            assertEquals("ORCHESTRATOR_RESTART", sig.reason());
            assertEquals(ts, sig.issuedAt());
        }

        @Test
        @DisplayName("instanciable avec executionId non-null (cible)")
        void instantiableWithNonNullExecutionId() {
            var id = signal();
            var ts = now();
            var sig = new ScenarioRestartSignal(id, exec(), "MANUAL_RESTART", ts);

            assertEquals(id, sig.id());
            assertEquals(exec(), sig.executionId());
            assertEquals("MANUAL_RESTART", sig.reason());
            assertEquals(ts, sig.issuedAt());
        }

        @Test
        @DisplayName("egalite par valeur")
        void valueEquality() {
            var id = signal();
            var ts = now();
            var s1 = new ScenarioRestartSignal(id, null, "RESTART", ts);
            var s2 = new ScenarioRestartSignal(id, null, "RESTART", ts);

            assertEquals(s1, s2);
            assertEquals(s1.hashCode(), s2.hashCode());
        }

        @Test
        @DisplayName("reason different -> pas egaux")
        void differentReasonNotEqual() {
            var id = signal();
            var ts = now();
            var s1 = new ScenarioRestartSignal(id, null, "RESTART", ts);
            var s2 = new ScenarioRestartSignal(id, null, "OTHER", ts);

            assertNotEquals(s1, s2);
        }

        @Test
        @DisplayName("id null leve NullPointerException")
        void nullIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ScenarioRestartSignal(null, null, "RESTART", now()));
        }

        @Test
        @DisplayName("reason null leve NullPointerException")
        void nullReasonThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ScenarioRestartSignal(signal(), null, null, now()));
        }

        @Test
        @DisplayName("issuedAt null leve NullPointerException")
        void nullIssuedAtThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ScenarioRestartSignal(signal(), null, "RESTART", null));
        }
    }

    // ─── toString verifications ─────────────────────────────────────────

    @Nested
    @DisplayName("toString coverage")
    class ToStringCoverage {

        @Test
        @DisplayName("tous les events/signals ont toString avec leur nom")
        void allEventsToStringContainsName() {
            var ts = now();
            var desc = sampleDescriptor();
            var id = signal();

            assertTrue(new AssertionPassed(exec(), task(), ts).toString().contains("AssertionPassed"));
            assertTrue(new AssertionFailed(exec(), task(), "reason", ts).toString().contains("AssertionFailed"));
            assertTrue(new AgentRegistered(agent(), desc, ts).toString().contains("AgentRegistered"));
            assertTrue(new AgentLost(agent(), "timeout", ts).toString().contains("AgentLost"));
            assertTrue(new AgentRecovered(agent(), ts).toString().contains("AgentRecovered"));
            assertTrue(new ReportGenerated(exec(), report(), ts).toString().contains("ReportGenerated"));
            assertTrue(new ReportPublished(exec(), report(), "CONFLUENCE", ts).toString().contains("ReportPublished"));
            assertTrue(new ScenarioRestartSignal(id, null, "RESTART", ts).toString().contains("ScenarioRestartSignal"));
        }
    }
}

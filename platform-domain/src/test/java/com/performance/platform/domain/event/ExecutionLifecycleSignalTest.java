package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link ExecutionLifecycleSignal} et {@link LifecycleAction}.
 * <p>
 * Couvre : factories, validation non-null, defensive copy,
 * stopBehavior(), constantes, et AgentSignal sealed interface.
 */
@DisplayName("ExecutionLifecycleSignal")
class ExecutionLifecycleSignalTest {

    // ─── Helpers ──────────────────────────────────────────────────────

    private static ExecutionId exec() { return ExecutionId.of("exec-1"); }
    private static TaskId task() { return TaskId.of("task-1"); }
    private static SignalId signal() { return SignalId.generate(); }

    // ─── LifecycleAction enum ─────────────────────────────────────────

    @Nested
    @DisplayName("LifecycleAction enum")
    class LifecycleActionTest {

        @Test
        @DisplayName("should contain START and STOP")
        void shouldContainStartAndStop() {
            assertEquals(2, LifecycleAction.values().length);
            assertSame(LifecycleAction.START, LifecycleAction.valueOf("START"));
            assertSame(LifecycleAction.STOP, LifecycleAction.valueOf("STOP"));
        }
    }

    // ─── Factory start() ──────────────────────────────────────────────

    @Nested
    @DisplayName("Factory start()")
    class StartFactoryTest {

        @Test
        @DisplayName("should create with LifecycleAction.START")
        void shouldCreateWithStartAction() {
            var sig = ExecutionLifecycleSignal.start(signal(), exec(), task(), Map.of());

            assertSame(LifecycleAction.START, sig.action());
        }

        @Test
        @DisplayName("should set issuedAt to the current time")
        void shouldSetIssuedAtToNow() {
            var before = Instant.now();
            var sig = ExecutionLifecycleSignal.start(signal(), exec(), task(), Map.of());
            var after = Instant.now();

            assertFalse(sig.issuedAt().isBefore(before));
            assertFalse(sig.issuedAt().isAfter(after));
        }
    }

    // ─── Factory stop() ───────────────────────────────────────────────

    @Nested
    @DisplayName("Factory stop()")
    class StopFactoryTest {

        @Test
        @DisplayName("should create with LifecycleAction.STOP")
        void shouldCreateWithStopAction() {
            var sig = ExecutionLifecycleSignal.stop(signal(), exec(), task(), Map.of());

            assertSame(LifecycleAction.STOP, sig.action());
        }

        @Test
        @DisplayName("should set issuedAt to the current time")
        void shouldSetIssuedAtToNow() {
            var before = Instant.now();
            var sig = ExecutionLifecycleSignal.stop(signal(), exec(), task(), Map.of());
            var after = Instant.now();

            assertFalse(sig.issuedAt().isBefore(before));
            assertFalse(sig.issuedAt().isAfter(after));
        }
    }

    // ─── Null validation ──────────────────────────────────────────────

    @Nested
    @DisplayName("Null validation")
    class NullValidationTest {

        @Test
        @DisplayName("should require non-null id")
        void shouldRequireNonNullId() {
            assertThrows(NullPointerException.class, () ->
                    new ExecutionLifecycleSignal(null, exec(), task(), LifecycleAction.START, Map.of(), Instant.now()));
        }

        @Test
        @DisplayName("should require non-null executionId")
        void shouldRequireNonNullExecutionId() {
            assertThrows(NullPointerException.class, () ->
                    new ExecutionLifecycleSignal(signal(), null, task(), LifecycleAction.START, Map.of(), Instant.now()));
        }

        @Test
        @DisplayName("should require non-null taskId")
        void shouldRequireNonNullTaskId() {
            assertThrows(NullPointerException.class, () ->
                    new ExecutionLifecycleSignal(signal(), exec(), null, LifecycleAction.START, Map.of(), Instant.now()));
        }

        @Test
        @DisplayName("should require non-null action")
        void shouldRequireNonNullAction() {
            assertThrows(NullPointerException.class, () ->
                    new ExecutionLifecycleSignal(signal(), exec(), task(), null, Map.of(), Instant.now()));
        }

        @Test
        @DisplayName("should require non-null issuedAt")
        void shouldRequireNonNullIssuedAt() {
            assertThrows(NullPointerException.class, () ->
                    new ExecutionLifecycleSignal(signal(), exec(), task(), LifecycleAction.START, Map.of(), null));
        }

        @Test
        @DisplayName("should require non-null fields — id null")
        void shouldRequireNonNullFieldsId() {
            assertThrows(NullPointerException.class, () ->
                    new ExecutionLifecycleSignal(null, exec(), task(), LifecycleAction.START, Map.of(), Instant.now()));
        }

        @Test
        @DisplayName("should require non-null fields — executionId null")
        void shouldRequireNonNullFieldsExecutionId() {
            assertThrows(NullPointerException.class, () ->
                    new ExecutionLifecycleSignal(signal(), null, task(), LifecycleAction.START, Map.of(), Instant.now()));
        }

        @Test
        @DisplayName("should require non-null fields — taskId null")
        void shouldRequireNonNullFieldsTaskId() {
            assertThrows(NullPointerException.class, () ->
                    new ExecutionLifecycleSignal(signal(), exec(), null, LifecycleAction.START, Map.of(), Instant.now()));
        }

        @Test
        @DisplayName("should require non-null fields — action null")
        void shouldRequireNonNullFieldsAction() {
            assertThrows(NullPointerException.class, () ->
                    new ExecutionLifecycleSignal(signal(), exec(), task(), null, Map.of(), Instant.now()));
        }

        @Test
        @DisplayName("should require non-null fields — issuedAt null")
        void shouldRequireNonNullFieldsIssuedAt() {
            assertThrows(NullPointerException.class, () ->
                    new ExecutionLifecycleSignal(signal(), exec(), task(), LifecycleAction.START, Map.of(), null));
        }
    }

    // ─── Parameters handling ──────────────────────────────────────────

    @Nested
    @DisplayName("Parameters handling")
    class ParametersTest {

        @Test
        @DisplayName("should accept null parameters and replace with empty map")
        void shouldAcceptNullParametersAndReplaceWithEmptyMap() {
            var sig = new ExecutionLifecycleSignal(signal(), exec(), task(),
                    LifecycleAction.START, null, Instant.now());

            assertNotNull(sig.parameters());
            assertTrue(sig.parameters().isEmpty());
        }

        @Test
        @DisplayName("should defensive copy parameters")
        void shouldDefensiveCopyParameters() {
            var mutable = new HashMap<String, Object>();
            mutable.put("key", "value");

            var sig = new ExecutionLifecycleSignal(signal(), exec(), task(),
                    LifecycleAction.START, mutable, Instant.now());

            // Mutate the original map
            mutable.put("key", "changed");
            mutable.put("newKey", "newValue");

            // Internal map must not be affected
            assertEquals("value", sig.parameters().get("key"));
            assertFalse(sig.parameters().containsKey("newKey"));

            // Direct mutation of the returned map must throw
            assertThrows(UnsupportedOperationException.class, () ->
                    sig.parameters().put("another", "value"));
        }
    }

    // ─── stopBehavior() ───────────────────────────────────────────────

    @Nested
    @DisplayName("stopBehavior()")
    class StopBehaviorTest {

        @Test
        @DisplayName("should return immediate when parameters is empty")
        void shouldReturnImmediateForMissingStopBehavior() {
            var sig = new ExecutionLifecycleSignal(signal(), exec(), task(),
                    LifecycleAction.STOP, Map.of(), Instant.now());

            assertEquals("immediate", sig.stopBehavior());
        }

        @Test
        @DisplayName("should return immediate for unknown stop behavior value")
        void shouldReturnImmediateForUnknownStopBehavior() {
            var params = Map.of("stopBehavior", (Object) "slowDown");
            var sig = new ExecutionLifecycleSignal(signal(), exec(), task(),
                    LifecycleAction.STOP, params, Instant.now());

            assertEquals("immediate", sig.stopBehavior());
        }

        @Test
        @DisplayName("should return immediate for immediate stop behavior")
        void shouldReturnImmediate() {
            var params = Map.of("stopBehavior", (Object) "immediate");
            var sig = new ExecutionLifecycleSignal(signal(), exec(), task(),
                    LifecycleAction.STOP, params, Instant.now());

            assertEquals("immediate", sig.stopBehavior());
        }

        @Test
        @DisplayName("should return completeCurrentCycle for completeCurrentCycle stop behavior")
        void shouldReturnCompleteCurrentCycle() {
            var params = Map.of("stopBehavior", (Object) "completeCurrentCycle");
            var sig = new ExecutionLifecycleSignal(signal(), exec(), task(),
                    LifecycleAction.STOP, params, Instant.now());

            assertEquals("completeCurrentCycle", sig.stopBehavior());
        }

        @Test
        @DisplayName("should return gracePeriod for gracePeriod stop behavior")
        void shouldReturnGracePeriod() {
            var params = Map.of("stopBehavior", (Object) "gracePeriod");
            var sig = new ExecutionLifecycleSignal(signal(), exec(), task(),
                    LifecycleAction.STOP, params, Instant.now());

            assertEquals("gracePeriod", sig.stopBehavior());
        }

        @Test
        @DisplayName("should return correct stop behavior for all three valid values")
        void shouldReturnCorrectStopBehavior() {
            record TestCase(String input, String expected) {}

            var cases = new TestCase[]{
                    new TestCase("immediate", "immediate"),
                    new TestCase("completeCurrentCycle", "completeCurrentCycle"),
                    new TestCase("gracePeriod", "gracePeriod")
            };

            for (var tc : cases) {
                var params = Map.of("stopBehavior", (Object) tc.input);
                var sig = new ExecutionLifecycleSignal(signal(), exec(), task(),
                        LifecycleAction.STOP, params, Instant.now());
                assertEquals(tc.expected, sig.stopBehavior(),
                        "stopBehavior() for '" + tc.input + "' should return '" + tc.expected + "'");
            }
        }
    }

    // ─── Constants ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Constants")
    class ConstantsTest {

        @Test
        @DisplayName("should have correct PARAM_* constant values")
        void shouldHaveCorrectParamConstantValues() {
            assertEquals("intervalSeconds", ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS);
            assertEquals("stopBehavior", ExecutionLifecycleSignal.PARAM_STOP_BEHAVIOR);
            assertEquals("gracePeriodDuration", ExecutionLifecycleSignal.PARAM_GRACE_PERIOD_DURATION);
            assertEquals("taskName", ExecutionLifecycleSignal.PARAM_TASK_NAME);
            assertEquals("phase", ExecutionLifecycleSignal.PARAM_PHASE);
        }

        @Test
        @DisplayName("should have correct STOP_* constant values")
        void shouldHaveCorrectStopConstantValues() {
            assertEquals("immediate", ExecutionLifecycleSignal.STOP_IMMEDIATE);
            assertEquals("completeCurrentCycle", ExecutionLifecycleSignal.STOP_COMPLETE_CURRENT_CYCLE);
            assertEquals("gracePeriod", ExecutionLifecycleSignal.STOP_GRACE_PERIOD);
        }
    }

    // ─── AgentSignal interface ────────────────────────────────────────

    @Nested
    @DisplayName("AgentSignal sealed interface")
    class AgentSignalInterfaceTest {

        @Test
        @DisplayName("should implement AgentSignal")
        void shouldImplementAgentSignal() {
            var sig = ExecutionLifecycleSignal.start(signal(), exec(), task(), Map.of());
            assertInstanceOf(AgentSignal.class, sig);
        }

        @Test
        @DisplayName("should expose id() and issuedAt() via AgentSignal")
        void shouldExposeIdAndIssuedAt() {
            var id = signal();
            var sig = ExecutionLifecycleSignal.start(id, exec(), task(), Map.of());
            AgentSignal asSignal = sig;

            assertEquals(id, asSignal.id());
            assertNotNull(asSignal.issuedAt());
        }
    }

    // ─── Record structure ─────────────────────────────────────────────

    @Nested
    @DisplayName("Record structure")
    class RecordStructureTest {

        @Test
        @DisplayName("should be a record with correct components")
        void shouldBeRecordWithCorrectComponents() {
            var id = signal();
            var ts = Instant.now();
            var sig = new ExecutionLifecycleSignal(id, exec(), task(),
                    LifecycleAction.START, Map.of("key", "val"), ts);

            assertEquals(id, sig.id());
            assertEquals(exec(), sig.executionId());
            assertEquals(task(), sig.taskId());
            assertEquals(LifecycleAction.START, sig.action());
            assertEquals(Map.of("key", "val"), sig.parameters());
            assertEquals(ts, sig.issuedAt());
        }

        @Test
        @DisplayName("should have value equality")
        void shouldHaveValueEquality() {
            var id = signal();
            var ts = Instant.now();
            var sig1 = new ExecutionLifecycleSignal(id, exec(), task(),
                    LifecycleAction.START, Map.of("a", "b"), ts);
            var sig2 = new ExecutionLifecycleSignal(id, exec(), task(),
                    LifecycleAction.START, Map.of("a", "b"), ts);

            assertEquals(sig1, sig2);
            assertEquals(sig1.hashCode(), sig2.hashCode());
        }

        @Test
        @DisplayName("should differ by action")
        void shouldDifferByAction() {
            var id = signal();
            var ts = Instant.now();
            var sig1 = new ExecutionLifecycleSignal(id, exec(), task(),
                    LifecycleAction.START, Map.of(), ts);
            var sig2 = new ExecutionLifecycleSignal(id, exec(), task(),
                    LifecycleAction.STOP, Map.of(), ts);

            assertNotEquals(sig1, sig2);
        }

        @Test
        @DisplayName("toString should contain class name")
        void toStringShouldContainClassName() {
            var sig = ExecutionLifecycleSignal.start(signal(), exec(), task(), Map.of());
            assertTrue(sig.toString().contains("ExecutionLifecycleSignal"));
        }
    }

    // ─── ScenarioRestartSignal unaffected ─────────────────────────────

    @Nested
    @DisplayName("Coexistence with ScenarioRestartSignal")
    class CoexistenceTest {

        @Test
        @DisplayName("ScenarioRestartSignal still implements AgentSignal")
        void scenarioRestartSignalStillImplementsAgentSignal() {
            var sig = new ScenarioRestartSignal(signal(), exec(), "RESTART", Instant.now());
            assertInstanceOf(AgentSignal.class, sig);
        }

        @Test
        @DisplayName("ScenarioRestartSignal is not ExecutionLifecycleSignal")
        void scenarioRestartSignalIsNotExecutionLifecycleSignal() {
            AgentSignal sig = new ScenarioRestartSignal(signal(), exec(), "RESTART", Instant.now());
            assertFalse(sig instanceof ExecutionLifecycleSignal);
        }

        @Test
        @DisplayName("ExecutionLifecycleSignal is not ScenarioRestartSignal")
        void executionLifecycleSignalIsNotScenarioRestartSignal() {
            AgentSignal sig = ExecutionLifecycleSignal.start(signal(), exec(), task(), Map.of());
            assertFalse(sig instanceof ScenarioRestartSignal);
        }
    }
}

# Assertion Distributed Analysis

**Date** : 2026-06-30
**Author** : System Designer
**Status** : DESIGN COMPLETE -- all 6 DQs resolved, PDRs produced, lifecycle signals designed

---

## 1. Decisions Summary (6 Deep-Dive Questions Resolved)

| Question | Topic | Decision | Rationale |
|---|---|---|---|
| DQ1 | Interval execution model | **Option B** -- Orchestrator-signaled lifecycle | Engine controls start/stop; assertion agents are stateful across injection duration |
| DQ2 | Deferred setup | Assertion PREPARATION phase runs before injection, configures assertion behavior | Some assertion types (Kafka consumer, HTTP mock) must be configured before injection starts |
| DQ3 | Agent assertion discovery | **Option A** -- `AssertionExecutor extends TaskExecutor` | An assertion IS a task. Agent zero-changes for lookup. Existing `TaskExecutionPipeline` works. |
| DQ4 | Local vs remote execution | **Option A** -- Automatic by executor type | Gatling = always local. Database/Kafka/WireMock/HttpMock/File = remote in DISTRIBUTED mode |
| DQ5 | History serialization | **Option C + A** -- History in `TaskResult.outputs` under `"history"` key, unique `AssertionSummary` format for final result | Agent holds history internally, sends structured final result via `TaskResult.outputs` |
| DQ6 | Interface evolution | **Option A** -- `AssertionExecutor extends TaskExecutor` | See Part 1 below for full comparison with Option C |

---

## 2. Part 1: ExecutionCapability Analysis (Option C vs Option A)

### 2.1 What Option C Would Look Like

Option C proposes a unified abstraction (`ExecutionCapability`) that both `TaskExecutor` and `AssertionExecutor` implement:

```java
// Hypothetical unified abstraction -- REJECTED
public interface ExecutionCapability {
    String name();
    TaskResult execute(ExecutionContext ctx, StepDefinition step);
}
```

Under this model:
- `TaskExecutor` would implement `ExecutionCapability` (or be removed)
- `AssertionExecutor` would implement `ExecutionCapability` (or be removed)
- A single registry replaces `TaskExecutorRegistry` + `AssertionExecutorRegistry`
- All capability resolution becomes: `executionCapabilityRegistry.getFor(taskName)`

### 2.2 Migration Cost Analysis

**Number of files touched**: ~35 files across all modules

| Module | Files Changed | Nature of Change |
|---|---|---|
| `platform-plugin-api` | 4 files | New `ExecutionCapability` interface, deprecate (or remove) `TaskExecutor`, `AssertionExecutor` |
| `platform-domain` | 2 files | Potentially remove `AssertionResult` (replaced by `TaskResult`), update `Evidence` |
| `platform-assertion` | 8 files | All 6 assertion executors change `implements AssertionExecutor` to `implements ExecutionCapability`, signature change from `evaluate()` to `execute()`, return type change |
| `platform-infrastructure` | 5 files | `DefaultTaskExecutorRegistry` becomes unified registry; remove `AssertionExecutorRegistry` bean; merge `TaskExecutorLookup.findAssertionExecutor` |
| `platform-execution-engine` | 3 files | `DagPhaseExecutor` loses `executeAssertionStep` path; `TaskExecutorLookup` simplifies; `assertionResultToTaskResult()` removed |
| `platform-agent-runtime` | 4 files | `TaskExecutionPipeline` already uses `TaskExecutor` -- would need `ExecutionCapability` instead; `LocalAgent`/`DistributedAgentRuntime` constructors change |
| `platform-injection-gatling` | 1 file | `GatlingTaskExecutor` changes interface |
| `platform-reporting` | 5 files | `AssertionReportEntry` changes; `VerdictCalculator` changes |
| Plugins (external) | Arbitrary | All existing plugins break -- `AssertionExecutor` interface removed |
| Tests | ~15 files | All assertion tests change |

**ADR impact**: New ADR required for each of:
1. Introduction of `ExecutionCapability` and deprecation of `AssertionExecutor`
2. Migration plan for existing plugins
3. Version bump for `platform-plugin-api` (breaking change)

**Risk**: BREAKING CHANGE for external plugins. Any plugin implementing `AssertionExecutor` would need to be recompiled and re-released. This violates CF-08 (stable `platform-plugin-api`).

### 2.3 What Option A Looks Like (Chosen)

```java
// Option A -- AssertionExecutor extends TaskExecutor
public interface AssertionExecutor extends TaskExecutor {
    // Inherits: TaskResult execute(ExecutionContext ctx, StepDefinition step);
    // Inherits: String getSupportedTaskName();

    /**
     * Default implementation delegates to evaluate().
     * Override for advanced lifecycle control (setup/sample/teardown).
     */
    @Override
    default TaskResult execute(ExecutionContext context, StepDefinition step) {
        return evaluateAsTaskResult(context, step);
    }

    // Provided by the default interface method (no per-executor boilerplate)
    private TaskResult evaluateAsTaskResult(ExecutionContext context, StepDefinition step) {
        var assertionResult = evaluate(context, step);
        return AssertionResultMapper.toTaskResult(assertionResult, step);
    }

    /**
     * Core evaluation logic -- same as today.
     * Interval-aware executors override execute() directly.
     */
    AssertionResult evaluate(ExecutionContext context, StepDefinition step);
}
```

**Why Option A wins decisively**:
1. **Zero breaking changes** to `AssertionExecutor` interface -- `evaluate()` still exists
2. **Backward compatible** -- existing assertion executors compile without changes (they get `execute()` for free)
3. **Agent-side zero changes** -- `TaskExecutionPipeline` already resolves `TaskExecutor` by name; assertion executors become resolvable via the same `taskExecutors` map
4. **Dual registry eliminated** -- a single `TaskExecutorRegistry` contains both preparation/injection executors AND assertion executors
5. **Single lookup path** -- `TaskExecutorLookup.findAssertionExecutor()` becomes unnecessary, removed
6. **No ADR required** for interface evolution -- `AssertionExecutor extends TaskExecutor` is additive, not breaking
7. **Plugin compatibility** -- external assertion plugins compiled against old `AssertionExecutor` continue to work (they still have `evaluate()`)
8. **Default bridge** -- the `execute()` -> `evaluate()` -> `AssertionResult` -> `TaskResult` conversion is in the interface default method, not duplicated in every executor

### 2.4 What Changes in Each Module (Option A)

| Module | Change | Effort |
|---|---|---|
| `platform-plugin-api` | `AssertionExecutor extends TaskExecutor` + `execute()` default method | LOW -- 1 file, additive only |
| `platform-assertion` | All 6 executors still compile; `AssertionExecutorRegistry` deprecated | LOW -- 1 new mapper shared method |
| `platform-infrastructure` | `DefaultTaskExecutorRegistry` now also receives `AssertionExecutor` beans (they implement `TaskExecutor`) | LOW -- automatic Spring injection |
| `platform-execution-engine` | `DagPhaseExecutor`: assertion steps use `findTaskExecutor()` instead of `findAssertionExecutor()`; `assertionResultToTaskResult()` still exists (called by `execute()` default) | LOW |
| `platform-agent-runtime` | `TaskExecutionPipeline` works as-is (assertion executors are `TaskExecutor` beans) | ZERO |
| `platform-domain` | New `AssertionSummary` record (see Part 2) | LOW |
| `platform-reporting` | `AssertionReportEntry` may reference `AssertionSummary` | LOW |

---

## 3. Part 2: Unified AssertionResult Format

### 3.1 The Problem

Today's `AssertionResult` has two structural issues:

1. **Dual return type**: `TaskExecutor.execute()` returns `TaskResult`; `AssertionExecutor.evaluate()` returns `AssertionResult`. After `AssertionExecutor extends TaskExecutor`, the over-the-wire format is always `TaskResult`. The rich assertion-specific structure (verdict, description, evidence) must survive serialization within `TaskResult.outputs`.

2. **The user wants a UNIQUE FORMAT** for all assertion results: `verdict` (pass/fail) + `description/justification` (text for report) + `collected data / metrics` used in the decision.

### 3.2 Design: AssertionSummary

A new domain record that encodes everything the report needs from an assertion, embedded within `TaskResult.outputs` under the key `"assertion"`:

```java
package com.performance.platform.domain.assertion;

import com.performance.platform.domain.id.TaskId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured result of an assertion evaluation, designed for report generation
 * and serialization. All assertion executors produce exactly this format in
 * their {@link TaskResult#outputs()} under the key {@code "assertion"}.
 * <p>
 * Record immuable -- 0 annotation framework.
 */
public record AssertionSummary(
    TaskId assertionId,
    AssertionVerdict verdict,          // PASSED | FAILED | SKIPPED | ERROR
    String description,                // human-readable justification for the report
    Map<String, Object> collectedData, // metrics/samples/data used in the decision
    List<AssertionSample> history,     // interval-based sampling history (empty if point-in-time)
    Duration evaluationDuration,
    Instant evaluatedAt
) {
    public AssertionSummary {
        Objects.requireNonNull(assertionId, "assertionId required");
        Objects.requireNonNull(verdict, "verdict required");
        Objects.requireNonNull(description, "description required");
        Objects.requireNonNull(evaluationDuration, "evaluationDuration required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt required");
        collectedData = collectedData == null ? Map.of() : Map.copyOf(collectedData);
        history = history == null ? List.of() : List.copyOf(history);
    }

    public boolean isPassed() {
        return verdict == AssertionVerdict.PASSED;
    }
}
```

### 3.3 AssertionVerdict (new enum, replaces AssertionStatus in public API)

```java
package com.performance.platform.domain.assertion;

/**
 * Verdict d'une evaluation d'assertion.
 * Distinct de {@link AssertionStatus} -- AssertionStatus reste interne aux executors,
 * AssertionVerdict est le format de sortie publique pour le transport et le rapport.
 */
public enum AssertionVerdict {
    PASSED,
    FAILED,
    SKIPPED,
    ERROR
}
```

**Design note**: `AssertionStatus` (existing) and `AssertionVerdict` are intentionally separate. `AssertionStatus` is used internally by assertion executors in their existing `AssertionResult`. `AssertionVerdict` is the serialization-safe format placed in `AssertionSummary` (which goes into `TaskResult.outputs`). A clean migration path: keep both for one release cycle, then deprecate `AssertionStatus` when `AssertionResult` is fully internalized. Alternatively, since `AssertionSummary` is new, we use `AssertionStatus` directly to avoid duplication. **Decision**: Use `AssertionStatus` for both -- no new enum. It already exists in `platform-domain`, has the right values, and is serializable.

### 3.4 AssertionSample (for interval-based assertions)

```java
package com.performance.platform.domain.assertion;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A single sample taken during interval-based assertion monitoring.
 * Each sample records the observed value at a point in time.
 * Record immuable -- 0 annotation framework.
 */
public record AssertionSample(
    Instant sampledAt,
    double observedValue,
    String unit,
    Map<String, Object> metadata   // extra context (e.g., topic partition offset)
) {
    public AssertionSample {
        Objects.requireNonNull(sampledAt, "sampledAt required");
        Objects.requireNonNull(unit, "unit required");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
```

### 3.5 Relationship to TaskResult.outputs

When an assertion executor completes (whether point-in-time or interval-based), it places an `AssertionSummary` in `TaskResult.outputs`:

```java
// Example: GatlingMetricAssertionExecutor produces:
Map<String, Object> outputs = Map.of(
    "assertion", new AssertionSummary(
        step.id(),
        AssertionStatus.PASSED,
        "PASSED: p95 420.00 ms < 500.00 ms",
        Map.of(
            "metric", "p95",
            "actualValue", 420.0,
            "expectedValue", 500.0,
            "operator", "LT",
            "unit", "ms",
            "simulationClass", "com.performance.CustomerApiSimulation",
            "totalRequests", 50000L
        ),
        List.of(),  // no history -- point-in-time
        Duration.ofMillis(2),
        Instant.now()
    )
);

TaskResult result = TaskResult.success(step.id(), "gatling-metric", duration, outputs);
```

For interval-based assertions (e.g., WireMock assertion monitoring during injection):

```java
Map<String, Object> outputs = Map.of(
    "assertion", new AssertionSummary(
        step.id(),
        AssertionStatus.PASSED,
        "PASSED: WireMock request count 1523 >= 1000 over 30s window",
        Map.of(
            "mockUrl", "http://wiremock:8080",
            "finalCount", 1523,
            "expectedCount", 1000,
            "operator", "GTE",
            "sampleCount", 6
        ),
        List.of(
            new AssertionSample(/* t=5s  */ Instant.parse("..."), 245, "requests", Map.of()),
            new AssertionSample(/* t=10s */ Instant.parse("..."), 520, "requests", Map.of()),
            new AssertionSample(/* t=15s */ Instant.parse("..."), 812, "requests", Map.of()),
            new AssertionSample(/* t=20s */ Instant.parse("..."), 1098, "requests", Map.of()),
            new AssertionSample(/* t=25s */ Instant.parse("..."), 1311, "requests", Map.of()),
            new AssertionSample(/* t=30s */ Instant.parse("..."), 1523, "requests", Map.of())
        ),
        Duration.ofSeconds(30),
        Instant.now()
    )
);

TaskResult result = TaskResult.success(step.id(), "wiremock", duration, outputs);
```

### 3.6 AssertionResult (existing) -- Status: RETAINED, internal-only

The existing `AssertionResult` record is **retained** as an internal domain object used by assertion executors. It is **not** the serialization format. The bridge from `AssertionResult` to `TaskResult` (via `AssertionSummary`) is handled by a shared utility class.

```java
// In platform-assertion or a shared utility
public final class AssertionResultMapper {

    private AssertionResultMapper() {}

    /**
     * Converts an internal AssertionResult to a TaskResult with AssertionSummary in outputs.
     * Point-in-time assertions produce empty history.
     */
    public static TaskResult toTaskResult(
            AssertionResult inner,
            StepDefinition step,
            List<AssertionSample> history  // empty list for point-in-time assertions
    ) {
        Map<String, Object> collectedData = (inner.evidence() != null)
                ? inner.evidence().details()
                : Map.of();

        var summary = new AssertionSummary(
                inner.assertionId(),
                inner.status(),  // AssertionStatus IS the verdict
                inner.description(),
                collectedData,
                history,
                inner.evaluationDuration(),
                inner.evaluatedAt()
        );

        TaskStatus taskStatus = switch (inner.status()) {
            case PASSED  -> TaskStatus.SUCCESS;
            case FAILED  -> TaskStatus.FAILED;
            case SKIPPED -> TaskStatus.SKIPPED;
            case ERROR   -> TaskStatus.FAILED;
        };

        return new TaskResult(
                inner.assertionId(),
                step.taskName(),
                taskStatus,
                inner.evaluationDuration(),
                Map.of("assertion", summary),
                taskStatus == TaskStatus.FAILED ? inner.description() : null,
                null,
                inner.evaluatedAt()
        );
    }
}
```

---

## 4. Part 3: Orchestrator-Signaled Lifecycle (DQ1-B)

### 4.1 The "Start Sampling" Signal

The orchestrator sends a new signal type to assertion agents to start monitoring. This is NOT a `TaskExecutionRequest` (which implies a single task execution model) -- it is a lifecycle signal that transitions the agent into a continuous monitoring mode.

**Design**: New event type `TASK_START_MONITORING` carried via the existing `ExecutionEvent` channel.

```java
// New event type constant in ExecutionEvent:
public static final String TASK_START_MONITORING = "TaskStartMonitoring";

// Payload shape (Map<String, Object>):
// {
//   "taskId": "wiremock-assertion-1",
//   "taskName": "wiremock",
//   "intervalSeconds": 5,
//   "parameters": { "mockUrl": "http://wiremock:8080", "operator": "GTE", "value": 1000 }
// }
```

However, a cleaner approach reuses the existing `TaskExecutionRequest` mechanism with a new field signaling the lifecycle mode:

### 4.2 Alternative (Chosen): Lifecycle field on TaskExecutionRequest

Rather than a new event type, add a `lifecycle` field to `StepDefinition` or to the request itself:

```java
// New enum in platform-domain
public enum TaskLifecycle {
    ONCE,              // existing behavior -- execute once, return result
    INTERVAL,           // execute setup, then sample periodically
    STOP_SAMPLING       // signal to stop sampling and return final result
}
```

When the engine dispatches an assertion step with `lifecycle=INTERVAL`:
1. Agent receives `TaskExecutionRequest` with `lifecycle=INTERVAL` + step parameters (e.g., interval=5s)
2. Agent performs setup (e.g., registers HTTP callback, opens connection)
3. Agent publishes `TaskClaimedByAgent` (standard claim)
4. Agent starts periodic sampling -- publishes `TaskWorkInProgress` at each sample interval
5. When injection completes, orchestrator sends a second `TaskExecutionRequest` with `lifecycle=STOP_SAMPLING` and same `taskId`
6. Agent stops sampling, computes final `AssertionSummary`, and publishes `TaskCompleted`

**Problem with this approach**: `TaskExecutionRequest` currently has a 1:1 relationship with `TaskResult`. Sending a second request for the same taskId breaks the `TaskCorrelationTracker` model.

### 4.3 Chosen Design: New Phase + New Signal

After analysis, the cleanest approach is:

1. **New Phase**: Add `ASSERTION_INTERVAL` phase to the `Phase` enum (in `platform-domain`)
2. **New Signal Type**: Add `PhaseSignal` to the `AgentSignal` sealed hierarchy
3. **Coordinated lifecycle**: Engine broadcasts `PhaseSignal.START` before INJECTION begins, `PhaseSignal.COMPLETE` after INJECTION ends

```java
// platform-domain: Phase enum
public enum Phase {
    PREPARATION,
    INJECTION,
    ASSERTION,
    ASSERTION_INTERVAL  // NEW -- runs concurrently with INJECTION
}

// New signal
public record PhaseSignal(
    SignalId signalId,
    ExecutionId executionId,
    Phase phase,
    PhaseAction action,    // START or COMPLETE
    Instant timestamp
) implements AgentSignal {}

public enum PhaseAction {
    START,     // Begin monitoring for this phase
    COMPLETE   // Stop monitoring, produce final results
}
```

**Execution flow**:

```
1. Engine builds ExecutionPlan:
   - PREPARATION steps (same as today)
   - INJECTION steps (same as today)
   - ASSERTION_INTERVAL steps (NEW -- these run during INJECTION)
   - ASSERTION steps (same as today)

2. Engine executes PREPARATION phase (standard DAG)

3. Engine checks for ASSERTION_INTERVAL steps:
   a. Dispatches ASSERTION_INTERVAL steps via broadcast
      - Agents claim and set up monitoring (start Kafka consumer, register HTTP callback, etc.)
      - Agents publish TaskClaimedByAgent
      - Agents are now MONITORING (new AgentState? Or reuse EXECUTING)
   b. Broadcasts PhaseSignal(phase=ASSERTION_INTERVAL, action=START)
      - Agents begin periodic sampling
   c. Engine dispatches INJECTION steps concurrently
      - Injection runs on injection agents
      - Assertion agents sample independently in background
   d. When all INJECTION steps complete:
      - Broadcasts PhaseSignal(phase=ASSERTION_INTERVAL, action=COMPLETE)
      - Assertion agents stop sampling, compute final AssertionSummary
      - Assertion agents publish TaskCompleted with final result
   e. Engine moves to ASSERTION phase (standard DAG) for point-in-time assertions

4. Engine executes ASSERTION (standard DAG)
```

**Key insight**: `ASSERTION_INTERVAL` steps are dispatched BEFORE `INJECTION` begins. The engine does NOT wait for them to complete before starting injection. Instead, both phases run concurrently, with `PhaseSignal` providing the coordination boundary.

### 4.4 Implementation Impact

| Component | Change |
|---|---|
| `Phase` enum | Add `ASSERTION_INTERVAL` |
| `AgentSignal` sealed hierarchy | Add `PhaseSignal` |
| `ExecutionPlan` record | Add `List<ExecutionStep> assertionIntervalSteps` |
| `ExecutionPlanBuilder` | Populate new list |
| `DagPhaseExecutor` | New method `executeConcurrentPhases()` for ASSERTION_INTERVAL + INJECTION |
| `RemoteExecutionEngine` | Dispatch ASSERTION_INTERVAL before INJECTION; send PhaseSignal to start/stop |
| `LocalExecutionEngine` | Same logic, but local (no transport) |
| `transport.receiveSignal()` | Already supports `AgentSignal` -- `PhaseSignal` works as-is |
| Assertion executors | New interface methods: `setup()`, `sample()`, `teardown()` for interval-aware executors |
| `TaskExecutionPipeline` | Already handles `TaskExecutionRequest`; no change |
| `TaskResult.outputs` | Already accepts `AssertionSummary` -- interval history in `history` field |

### 4.5 Simpler Alternative (Phased Approach)

Given the complexity of the full orchestrator-signaled lifecycle, a phased implementation is recommended:

**Phase A (immediate -- this design round)**: Point-in-time assertions only. Assertion executors extend `TaskExecutor`, run in ASSERTION phase (post-injection), produce `AssertionSummary` in `TaskResult.outputs`. Same execution model as today, but assertions now work on distributed agents.

**Phase B (future)**: Interval-based assertions with `ASSERTION_INTERVAL` phase, `PhaseSignal`, and concurrent injection/assertion. Separate PDR and Issues.

**Decision**: Implement Phase A now. Phase B is deferred and will have its own ADR and PDRs. This document specifies the interfaces and domain types that support Phase B, so no rework is needed.

---

## 5. Architectural Impact Summary

| Module | Phase A Impact | Lifecycle Signals (NEW) |
|---|---|---|
| `platform-domain` | New: `AssertionSummary`, `AssertionSample` records | New: `LifecycleAction` enum, `ExecutionLifecycleSignal` record, `AgentSignal` sealed hierarchy updated |
| `platform-plugin-api` | Modified: `AssertionExecutor extends TaskExecutor` | None (signals are domain events, not plugin API) |
| `platform-assertion` | Modified: 6 executors now register as `TaskExecutor` beans | Modified: interval-aware executors read `ExecutionLifecycleSignal.parameters` |
| `platform-infrastructure` | Modified: `DefaultTaskExecutorRegistry` receives assertion executors | None |
| `platform-execution-engine` | Modified: `DagPhaseExecutor` uses unified lookup | Modified: `ExecutionPlan.assertionIntervalSteps`, `DagPhaseExecutor` dispatches START/STOP, `LocalExecutionEngine`/`RemoteExecutionEngine` coordinate `linkedTo` |
| `platform-agent-runtime` | Zero changes (auto-resolved via `TaskExecutor` map) | Modified: `AgentRuntime.onLifecycleSignal()`, `DefaultLifecycleSignalHandler` with sampling loop and stop behaviors |
| `platform-transport` | Zero changes | Zero changes (`ExecutionLifecycleSignal` implements `AgentSignal`, transported automatically) |
| `platform-reporting` | Modified: `AssertionReportEntry` reads `AssertionSummary` from `TaskResult.outputs` | Modified: Interval history in reports |
| `platform-scenario-dsl` | None (ASSERTION phase unchanged) | New YAML parameter: `linkedTo` on assertion steps, `stopBehavior`, `gracePeriodDuration`, `intervalSeconds` |

---

## 6. Open Questions Flagged

| # | Question | Status |
|---|---|---|
| 1 | Should `AssertionStatus` be replaced by `AssertionVerdict` in the serialization format, or keep both? | **RESOLVED**: Use `AssertionStatus` for both internal and serialization. It already exists, has correct semantics, and is in `platform-domain`. |
| 2 | Should the `AssertionExecutorRegistry` be removed or kept as a wrapper? | **RESOLVED**: Keep as deprecated delegate to `TaskExecutorRegistry` for backward compatibility, then remove in next major version. |
| 3 | Should `assertionResultToTaskResult()` be extracted from `DagPhaseExecutor` into a shared utility? | **RESOLVED**: Yes. New `AssertionResultMapper` in `platform-assertion` or `platform-domain`. |
| 4 | Should the `TaskExecutionRequest` carry a new `lifecycle` field for interval assertions? | **RESOLVED**: No. Replaced by `ExecutionLifecycleSignal` approach -- cleaner separation of concerns. |

## NEW DQs (2026-06-30, after user review)

| # | Question | Status |
|---|---|---|
| 5 | Should lifecycle signals be assertion-only or general-purpose? | **RESOLVED**: GENERAL-PURPOSE. `ExecutionLifecycleSignal` is sent for ALL task types (PREPARATION/INJECTION/ASSERTION), not only assertions. It carries `LifecycleAction` (START/STOP). For standard tasks, it's informational framing. For assertions with `linkedTo`, it delimits the monitoring window. |
| 6 | Should the `PhaseSignal` concept be replaced? | **RESOLVED**: YES. `PhaseSignal` + `ASSERTION_INTERVAL` phase replaced by `ExecutionLifecycleSignal` + `linkedTo` parameter. Simpler: no new Phase enum value, no separate phase signal type. The `ExecutionPlan` gets a new `assertionIntervalSteps` list for assertions with `linkedTo`. |
| 7 | How are `stopBehavior` / `gracePeriodDuration` transmitted? | **RESOLVED**: Read from `StepDefinition.parameters()` by the engine at START/STOP dispatch time, placed in `ExecutionLifecycleSignal.parameters()`. They are YAML-only parameters, NOT domain fields. |
| 8 | How does the transport handle the new signal? | **RESOLVED**: ZERO transport changes. `ExecutionLifecycleSignal` implements `AgentSignal`, which is already the parameter type for `transport.broadcastSignal()`. All 5 transport implementations (Kafka, RabbitMQ, HTTP, Socket, InMemory) support `AgentSignal` and will transparently transport the new signal. |

---

## 7. Final Recommendations

1. **Interface evolution**: `AssertionExecutor extends TaskExecutor` (Option A). Zero breaking changes. The migration is purely additive.

2. **Serialization format**: `AssertionSummary` in `TaskResult.outputs` under key `"assertion"`. Unified across all assertion types.

3. **Distributed execution**: Assertion executors become discoverable by agents via existing `TaskExecutor` bean resolution. Agent requires zero code changes.

4. **Lifecycle**: `ExecutionLifecycleSignal` with START/STOP actions for ALL task types. Assertions with `linkedTo` use START/STOP to delimit monitoring window concurrent with injection. `stopBehavior` controls sampling loop termination. All transport implementations transparently support the new signal via `AgentSignal` sealed hierarchy.

5. **Backward compatibility**: Retained `AssertionResult` as internal-only, `AssertionExecutor.evaluate()` unchanged, `AssertionExecutorRegistry` deprecated.

6. **Lifecycle general-purpose design**: START/STOP signals sent for ALL tasks, not just assertions. This provides a uniform framing that future features (progress tracking, cancellation, distributed tracing) can build on without redesign.

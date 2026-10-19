# Performance Platform — Architecture Review & Improvement Plan

> **Date:** 2026-10-04
> **Scope:** 14 Maven modules, 26 146 LOC main, 150 test classes, ~2 400 `@Test` methods.
> **Method:** direct inspection of code, poms and tests. Findings are evidence-based (file:line) but not exhaustive — not all 26k LOC were read.
> **Author:** architectural review (Claude Code, at the request of the Architect)

---

## Verdict

The domain and application layers are genuinely clean — that part of the architecture is real, not aspirational. Above them, the design has drifted:

- **LOCAL and DISTRIBUTED are two parallel implementations of the same logic** (~1 900 LOC duplicated).
- **The plugin boundary is crossed by compile-time dependencies** (core → adapter, adapter → adapter).
- **The modularity guarantees claimed in CLAUDE.md are enforced by no test at all.**

The system works. It will not absorb the next feature without paying compounding interest.

---

## 1. What was verified (hard numbers)

| Claim in CLAUDE.md | Reality |
|---|---|
| `platform-domain` : 0 annotation Spring/JPA/Jackson | ✅ **True.** Zero framework imports. Enforced by `DomainArchitectureTest`. |
| Modules Modulith : communication via events uniquement, *détectée par ArchUnit test* | ❌ **False.** No test calls `modules.verify()`. See **A5**. |
| Routing by `task:` name, no `agentTags` (ADR-015) | ✅ Consistent — no `agentTags` / `AgentAllocator` references found. |
| AGENT exposes no web server (`WebApplicationType.NONE`, ADR-019) | ⚠️ **Partially true** — hole found. See **B5**. |
| Env var priority over properties (ADR-006) | ✅ Implemented — twice. See **B5**. |

**Tests** — 2 477 test methods. `@Disabled`: 0 in most modules, **6 in `platform-infrastructure`**.

**CI: none.** No `.github/workflows`, no `.gitlab-ci.yml`, no Jenkinsfile.

---

## 2. Findings

### A. Structural / modularity

#### A1 — The headline problem: LOCAL and DISTRIBUTED are duplicated implementations

`LocalExecutionEngine` (412 LOC) and `RemoteExecutionEngine` (576 LOC) share **193 of ~310/410 normalized lines (62% / 47%)** — including identical private helpers: `initializeExecution`, `computeVerdict`, `applyVerdict`, `finalizeExecution`, `checkFailedInPhase`, `checkSkippedInAnyPhase`, `checkSkippedInSteps`, `dispatchLifecycleSignal`, `createInitialState`, `updateState`, `updatePhaseInState`.

`LocalAgent` (444 LOC) and `DistributedAgentRuntime` (472 LOC) share **193 of ~285/307 lines (68% / 63%)** — identical fields (`activeTaskCount`, `processedMessageIds`, `activeTasks`, `taskExecutorService`, `lifecycleLock`) and identical method lists.

This is exactly what `ExecutionTransport` was created to prevent. The port exists, `InMemoryExecutionTransport` exists — yet the engine and agent were **forked on `runtime.mode` instead of being parameterized by the transport**.

> **Impact:** every bug fix must be applied twice; every feature twice.

#### A2 — The assertion unification (ISSUE-152…156) is half-finished, and the deprecated path is the live one

`AssertionExecutorRegistry` and `DefaultAssertionExecutorRegistry` are `@Deprecated(forRemoval = true)` — but:

- `RuntimeAssemblyConfiguration:70` still injects the registry.
- `RegistryTaskExecutorLookup:54` implements `findAssertionExecutor` by delegating to it.
- `AssertionExecutor extends TaskExecutor` (`TaskExecutorLookup:34`) — so the separate registry is *structurally redundant*.

Six commits of deprecation work left **two coexisting paths with the old one still wired**. Finish it or revert it; do not leave it.

#### A3 — Layering is broken by compile-scope dependencies

Verified in poms — all `compile` scope:

```
platform-execution-engine  →  platform-assertion   →  platform-infrastructure
platform-assertion         →  platform-injection-gatling
platform-reporting         →  platform-injection-gatling
platform-execution-engine  →  platform-agent-runtime
```

Two distinct problems:

- **Core depends on adapter.** The engine has a compile-time dependency on the assertion module — which defeats the plugin architecture (ADR-007). The engine should resolve assertion executors through its registry port, never through a module dependency.
- **Adapter depends on adapter.** `platform-assertion` (a provider module) depends on `platform-infrastructure` (the adapter module). `platform-reporting` depends on `platform-injection-gatling` — so you cannot ship a report without shipping the Gatling injection engine, and you cannot test reporting without Gatling on the classpath.

#### A4 — `ExecutionTransport` is a fat interface: 12 methods × 5 implementations

`dispatchTask`, `broadcastSignal`, `publishEvent`, `subscribe`, `receiveTask`, `receiveSignal`, `connect`, `disconnect`, `isConnected`, `getType`, `publishAgentEvent`, `subscribeAgentEvents` — implemented by InMemory, Kafka, RabbitMQ, HTTP, Socket.

Adding any method means editing 5 classes. The javadoc even warns *"toute modification nécessite un ADR"*, which is a symptom, not a safeguard.

> **Fix:** Interface Segregation — `TaskChannel`, `SignalChannel`, `EventChannel`, `LifecycleChannel`, `ConnectionLifecycle`.

#### A5 — Spring Modulith is decorative

`ModulithVerificationTest:27` asserts only `modules.isNotNull()`, `isNotEmpty()`, and that some module name contains `"infrastructure"` or `"app"`. It **never calls `modules.verify()`**.

The javadoc admits it:

> *"La verification complete des violations inter-modules se fera dans les tests E2E (ISSUE-082)"*

That issue never landed. Additionally, Modulith operates on package structure *within one artifact* — with 14 separate Maven modules it **cannot see the inter-module edges at all**.

> **Consequence:** the modularity rule is enforced by nothing, which is precisely why A1 and A3 went unnoticed. You are paying for a dependency that provides nothing.

#### A6 — Two parallel models for agent lifecycle; the domain one is dead code

`AgentRegistered` / `AgentLost` / `AgentRecovered` are constructed **only in tests** (`SignalsTest.java`).

Production uses `TransportAgentRegistration`, which publishes `AgentLifecycleEvent` with a hand-built `Map<String, Object>` payload — carrying the comment:

> *"NOTE: synchroniser avec AgentDescriptor si le record évolue"*

So the lifecycle schema is maintained manually in two places, one of which is untyped strings, and **the type-safe version is unused**. Meanwhile `EventHierarchyArchTest` spends real, well-built enforcement on the `AgentSignal` sealed interface — a model nothing consumes.

---

### B. Logic / correctness

#### B1 — `ExecutionContext` is immutable but has two design defects

- **`with()` is O(store size)** (`ExecutionContext.java:60-73`): it deep-copies the entire nested map on *every* task result. Inserting N results costs **O(N²)**. For a load-test platform where result counts scale with task × agent, this is the wrong cost curve. A persistent/immutable map (or a `ConcurrentHashMap` behind a safe wrapper) fixes it without giving up immutability.
- **Lookup is type-erased and order-dependent.** `get(taskId, agentId, Class<T>)` scans `result.outputs().values()` and returns *the first value assignable to the type* (`:83-97`); `getFirst(taskId, Class<T>)` iterates a `HashMap`'s values. Two `String` outputs → **which one you get is undefined**. `Class<T>` is being used as a key when it is not one.

> **Fix:** named output keys.

#### B2 — Result selection under multi-claim is non-deterministic

`RemoteStepDispatcher.awaitCompletion` picks:

```java
results.values().stream().filter(TaskResult::isSuccess).findFirst()
```

First-successful out of a `HashMap` (`CorrelationState.results` is a `ConcurrentHashMap`).

With ADR-011 multi-claim (several agents execute the same task), **which agent's measurement you report is arbitrary**.

> For a platform whose output *is* a measurement, this is a **correctness bug**, not a style issue.
> **Fix:** an explicit, documented selection policy (first-completed by timestamp, median, or designated agent).

#### B3 — Agent death costs a full 300 s stall

`isAllComplete` requires *every claiming agent* to complete **or** fail. An agent that crashes, is OOM-killed, or is partitioned reports neither — so `ALL_COMPLETE` is **unsatisfiable** and the step blocks until `taskExecutionTimeout` (default **300 s**, `ExecutionEngineProperties:35`).

There is **no fast-fail on claim loss**. `AgentLost` exists as a domain record and is never emitted (see B4), so the engine has no signal to react to.

#### B4 — Dead-agent detection is not wired at all

`AgentTtlMonitor` is **not** a `@Component`, **not** `@Scheduled`, and **not** instantiated by `AgentRegistryConfiguration` (which only creates `InMemoryAgentRegistry`).

Nothing evicts expired agents. Consequence chain:

```
heartbeats stop
  → registry keeps the entry forever
  → DefaultAgentAvailabilityChecker.awaitAgentFor returns the DEAD agent as available
  → orchestrator dispatches into the void
  → B3 stall (300 s)
```

> **Agent-loss handling is architecturally present and operationally absent.**

#### B5 — The ADR-019 "JVM-level guarantee" has a hole

`PerformancePlatformApplication.isAgentMode()` (`:100-113`) checks `System.getenv("MODE")` and `System.getProperty("runtime.role")`. It does **not** check Spring's command-line args.

So `java -jar app.jar --runtime.role=AGENT` is **not detected** — the app starts **with a web server** in what the operator believes is AGENT mode, contradicting the documented guarantee:

> *"Cette garantie est au niveau JVM, pas au niveau d'une property Spring."*

Mode/role parsing is also implemented **three times** (`main()`, `RuntimeModeResolver`, `RuntimeConfigEnvironmentPostProcessor`) — the duplication is what let the hole in.

#### B6 — The DSL silently accepts typos

`YamlScenarioParser:53` sets `FAIL_ON_UNKNOWN_PROPERTIES = false`.

A misspelled key (`assertionss:`, `duraton:`) is **ignored** and the scenario runs with defaults. There is no JSON schema and no version field in the DSL.

> For a test platform, silently running a *different* scenario than the one written is the worst failure mode available.

---

### C. Efficiency

#### C1 — Broadcast dispatch is O(tasks × agents)

`dispatchTask` is a pure broadcast with agent-side filtering. Each of **A** agents deserializes and filters every one of **T** task requests.

Adding agents *increases* per-agent waste rather than throughput, and the orchestrator cannot direct work to a specific capacity. The doc frames this as deliberate, but it caps scalability — and it is the root cause of **B2** (multi-claim exists *because* you cannot target).

#### C2 — 500 ms polling on the DAG critical path

`POLL_COMPLETION_MS = 500`, declared **twice** (`RemoteExecutionEngine:104`, `RemoteStepDispatcher:39`).

Worst case adds **500 ms × DAG depth** to every run. A `CompletableFuture` / latch signalled by `onCompleted` removes the constant entirely.

#### C3 — Unbounded memory growth

`DefaultTaskCorrelationTracker.states` is a `ConcurrentHashMap` with **no eviction** — no `remove`, `clear`, or cleanup in the tracker or its interface. Every dispatched message leaves a permanent entry, including all results.

> A long-lived orchestrator leaks monotonically → eventual OOM.

#### C4 — `SocketExecutionTransport` is 755 LOC reimplementing a broker

Manual JSON framing, accept loop, reconnect with backoff, handshake, connection registry, 4 handler lists — for a capability Kafka and RabbitMQ already provide. It is the largest file in the repo and likely the least valuable.

---

### D. Operability

| # | Finding |
|---|---|
| **D1** | **No CI.** 2 477 tests that nothing runs automatically. All correctness claims above are unenforced in practice. |
| **D2** | **Integration tests are behind `-P integration-tests` (failsafe).** `KafkaExecutionTransportIT`, `RabbitMQExecutionTransportIT`, and 4 Testcontainers classes in `platform-app` do **not** run on `mvn test`. Without CI, they essentially never run. |
| **D3** | **6 `@Disabled` tests** in `platform-infrastructure` — unexplained, and nothing prevents more. |
| **D4** | **Security:** default `platform.security.enabled=false` → `anyRequest().permitAll()`; CSRF disabled; authentication only (no authorization/roles on `/api/**`). Acceptable for v1, but it is an opt-in posture with a permissive default. |
| **D5** | **`platform-deployment` and `platform-examples` are empty** (0 Java files). Two modules in the reactor that build nothing. |

---

### What is genuinely good (do not lose this in the rework)

- Immutable `ExecutionContext` + `PartialExecutionContext` split.
- Sealed event interfaces with **exhaustive `permits` enforcement** (`EventHierarchyArchTest`).
- `RetryPolicy` record with defensive copies and validated invariants.
- `TaskResult` / `Verdict` modelling.
- `TransportMessages` contract tests across transport implementations.
- The ArchUnit tests that *do* exist are well-written — the `PersistenceConfinementTest` `@Entity` predicate is sharp work.

---

## 3. What to redo — target architecture

One structural move unlocks most of the rest:

> **Delete the mode fork. There is one engine and one agent; LOCAL vs DISTRIBUTED is a transport choice plus an executor-location choice.**

```
                    ┌─────────────── platform-application (ports) ───────────────┐
                    │  ExecutionEnginePort   TaskDispatchPort   RegistryPort     │
                    └────────────────────────────────────────────────────────────┘
                                        ▲
        ┌───────────────────────────────┼───────────────────────────────┐
        │                               │                               │
  platform-execution-engine     platform-agent-runtime          (adapters)
   ONE ExecutionEngine            ONE TaskAgentRuntime
   - DagPhaseExecutor             - TaskExecutionPipeline
   - PlanBuilder                  - specialization filter
   - CompletionPolicy             - claim/complete publish
   - depends on PORTS only        - depends on PORTS only
        │                               │
        └──────────► platform-transport (segregated ports) ◄──────────┘
                     TaskChannel | SignalChannel | EventChannel | LifecycleChannel
                     └ InMemory ─ Kafka ─ Rabbit ─ HTTP ─ Socket ┘
```

### Concretely

1. **Merge the engines and the agents.** One `ExecutionEngine` taking `TaskDispatchPort`; one `TaskAgentRuntime` taking an execution strategy. Delete `LocalExecutionEngine`, `RemoteExecutionEngine`, `LocalAgent`, `DistributedAgentRuntime` — **~1 900 LOC replaced by ~900**.
2. **Segregate `ExecutionTransport`** into 4–5 narrow ports. Each implementation then only implements what it supports; `getType()` disappears.
3. **Cut the core → adapter edges.**
   - Remove `execution-engine → assertion`: the engine resolves via its registry port.
   - Remove `assertion → infrastructure`: extract shared test-support, or duplicate the small piece.
   - Remove `reporting → injection-gatling`: reporting consumes a **domain** metrics record, not Gatling types.
4. **Make Modulith earn its place, or remove it.** Either consolidate into a single Maven artifact where Modulith can actually verify module boundaries, or drop the dependency and enforce boundaries with a *cross-module* ArchUnit test (see §4). Do not keep it decorative.
5. **Delete the dead model.** Either emit `AgentRegistered` / `AgentLost` / `AgentRecovered` and use them, or delete them along with the `Map<String,Object>` payloads.
6. **One mode-resolution path.** A single `RuntimeConfig` bean, consumed by `main()` and by the conditions. No `System.getenv` in `main()`.
7. **Finish the assertion migration** — delete `AssertionExecutorRegistry` and `findAssertionExecutor`; one registry only.

---

## 4. Proving tests

Each phase ships with the test that would have caught the problem. These are the tests that turn the numbers above into a **ratchet** — they fail today and pass after the fix.

| # | Phase | Test that proves it | Kills |
|---|---|---|---|
| **T1** | **Cross-module boundary enforcement** — one ArchUnit test in `platform-app` asserting the module DAG: `engine` must not depend on `assertion` / `infrastructure`; `reporting` must not depend on `injection-gatling`; adapters never depended on by cores. | `ModuleDependencyArchTest` — **fails today**, passes after step 3 | A3, A5 |
| **T2** | **LOCAL ≡ DISTRIBUTED equivalence** — a parameterized E2E running the *same* scenario through both transports, asserting identical `Verdict` + task-status sequence. | `LocalDistributedEquivalenceTest` — **the single most valuable test in this repo** | A1, B1, B2 |
| **T3** | **Mode resolution** — `@ParameterizedTest` over `(env, property, cli-arg)` triples asserting resolved `(mode, role)`; plus a test that `--runtime.role=AGENT` yields `WebApplicationType.NONE`. | `RuntimeConfigResolutionTest` — **fails today** on the CLI-arg case | B5 |
| **T4** | **Agent death fast-fail** — kill an agent mid-task (Testcontainers or a lifecycle stub); assert the step fails well under the timeout **and** emits `AgentLost`. | `AgentLossRecoveryIT` — **fails today** | B3, B4 |
| **T5** | **Tracker eviction** — dispatch 10 000 messages; assert `states` size returns to 0 after completion, and that held reference count is bounded. | `CorrelationTrackerMemoryTest` — **fails today** | C3 |
| **T6** | **Deterministic result selection** — two agents return different results for one task; assert the documented policy selects the same one across 1 000 runs. | `ResultSelectionDeterminismTest` — **fails today** | B2 |
| **T7** | **DSL strictness** — a corpus of scenarios with an unknown key, a typo'd key, a wrong type, and a missing required field; assert each is rejected **with the offending line number**. | `ScenarioStrictnessTest` — **fails today** | B6 |
| **T8** | **Lifecycle event contract** — round-trip `AgentDescriptor` → transport payload → `AgentDescriptor`, assert equality, so the hand-written `Map` serializer cannot silently drift. | `AgentLifecycleRoundTripTest` | A6 |
| **T9** | **CI** — GitHub Actions running `mvn verify -P integration-tests` with Testcontainers on every push. | the workflow file itself | D1, D2 |

**T1 and T2 are the load-bearing ones.** T1 prevents the drift from recurring. T2 makes the merge of A1 *safe*: write it against current behaviour, watch it pass, then refactor and watch it still pass.

---

## 5. Priority

| Priority | Item | Effort | Payoff |
|---|---|---|---|
| **P0** | CI with integration profile (**T9**) | hours | Without it, nothing below stays fixed |
| **P0** | Wire `AgentTtlMonitor` + publish `AgentLost` + fast-fail (**B3, B4**) | days | Correctness under the system's *primary* failure mode |
| **P0** | Tracker eviction (**C3**) | hours | Prevents production OOM |
| **P1** | Cross-module ArchUnit + cut core → adapter edges (**A3, T1**) | days | Restores the plugin architecture |
| **P1** | Merge engines + agents behind ports (**A1, T2**) | 1–2 weeks | Halves the future cost of every change |
| **P1** | DSL strictness + schema version (**B6**) | days | Prevents silently-wrong test runs |
| **P2** | Segregate `ExecutionTransport` (**A4**) | days | Makes 5 implementations maintainable |
| **P2** | Deterministic result selection (**B2**) | days | Measurement integrity |
| **P2** | Finish assertion unification (**A2**) | days | Removes duplicate paths |
| **P3** | Decide Modulith's fate (**A5**); delete dead lifecycle model (**A6**); address `SocketExecutionTransport` (**C4**); fill or drop empty modules (**D5**) | — | Reduces surface area |

---

## One-line summary

> The domain and application layers are the good part and should not be touched. Everything above them needs the LOCAL/DISTRIBUTED fork **collapsed behind the transport port it was always supposed to use**, plus a **real cross-module boundary test** and a **CI pipeline** to make the boundary stick.

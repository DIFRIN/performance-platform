# ADR-065 - Injection adapter capabilities, strict rejection and rate guardrail

**Status** : PROPOSED (2026-11-16)
**Related** : ADR-057 (executors as capability modules), ADR-064, ADR-067, ADR-068, ADR-006

## Context

- ADR-064 makes the workload plan engine-neutral. An engine may not implement
  every plan faithfully (closed model, bursts, global scope, histograms).
- Today an unsupported or ill-formed parameter is discovered on the agent at
  run time, sometimes after load has started, and nothing prevents an adapter
  from approximating silently.
- A custom Java simulation (`simulation: <class>`) is treated like the
  generated HTTP/Kafka simulation, although the platform cannot know whether it
  honours the plan or how many requests one user sends.
- Nothing bounds the load an agent may be asked to generate. A typo in a rate
  can saturate a shared environment.

## Decision

### 1. Each injection adapter declares its capabilities

A value object in `platform-plugin-api`, with no Spring type:

```java
public record InjectionCapabilities(
        Set<ArrivalModel> models,
        Set<String> stageKinds,          // ramp, hold, burst, pause
        Set<String> protocols,
        boolean supportsGlobalScope,     // can scale a plan from a grant share (ADR-067)
        boolean supportsCancellation,    // ADR-066
        boolean providesHistogram,       // ADR-068
        boolean providesWindows) {}      // ADR-068
```

The executor exposes it through an optional interface
(`InjectionCapabilityProvider`). `TaskExecutor` is not modified.

### 2. Reject, never approximate

Before starting any load, the adapter checks the plan against its capabilities
and the step fails with an explicit error naming the unsupported feature. No
fallback, no closest equivalent. The check runs on the agent because the
orchestrator does not host the adapters (ADR-057); it runs before the first
arrival, so a rejected plan generates no traffic.

### 3. No request-rate model

The platform offers arrivals per second (open) and concurrent users (closed).
Requests per second is a **measured result** (ADR-068), not a target. A
`requests_per_second` unit is rejected at parse time.

### 4. Custom simulations are opaque unless they opt in

- A custom simulation that reads the platform injection steps and implements
  `InjectionCapabilityProvider` is treated like a generated one.
- Otherwise it is **opaque**: `scope: global` is rejected (ADR-067), offered-load
  integrity checks are reported as `INCONCLUSIVE`, and the report states that
  the load profile was defined by the simulation, not by the plan.

### 5. Per-agent rate guardrail

Agent configuration, environment variable first (ADR-006):

| Property | Env var | Meaning |
|---|---|---|
| `injection.max-arrival-rate` | `INJECTION_MAX_ARRIVAL_RATE` | highest users/s this agent accepts |
| `injection.max-concurrent-users` | `INJECTION_MAX_CONCURRENT_USERS` | highest closed-model population |

The agent compares its own share of the plan (after ADR-067 scaling) with
these limits and rejects the step before starting. Unset means no limit.

## Consequences

- Unsupported combinations fail fast with a clear message instead of producing
  a misleading run.
- A second engine can be added later by declaring capabilities; no core change.
- Custom simulations stay available as the path for complex journeys, with
  honest reporting of what the platform can and cannot guarantee.
- `platform-plugin-api` gains one record and one optional interface: public
  contract addition, reviewed by the Architect on acceptance.
- Agents in shared environments can be capped by operations without editing
  scenarios.

## Rejected alternatives

- **Validate capabilities on the orchestrator.** It would need every adapter on
  its classpath, against ADR-057.
- **Approximate unsupported stages** (for example a burst as a very short
  ramp). The run would no longer match the recorded plan.

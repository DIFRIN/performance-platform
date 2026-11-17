# ADR-064 - Composable, platform-owned workload plan

**Status** : ACCEPTED (2026-11-17)
**Supersedes** : the `LoadModel` / `LoadModelType` domain model (kept as YAML presets only)
**Related** : ADR-053 (inline model only), ADR-065, ADR-067, ADR-068

## Context

- A load profile exists in the domain only as `LoadModel(LoadModelType, Map)`:
  eight fixed types (`RAMP`, `RAMP_UP_DOWN`, `CONSTANT`, `SPIKE`, `STAIR`,
  `SOAK`, `BURST`, `CUSTOM`) with untyped parameters. A new shape needs a new
  enum value and a new branch in `DefaultLoadModelTranslator`.
- The only typed representation of the profile is Gatling's
  `OpenInjectionStep`, produced on the agent. The platform therefore cannot
  validate a profile before dispatch, split it between agents (ADR-067),
  compare planned and achieved load (ADR-068), or record what was really asked.
- `LoadModelTranslator` returns `List<OpenInjectionStep>`: the closed model
  (fixed population of concurrent users) cannot be expressed at all.
- Parameter validation happens at run time on the agent, after dispatch.

## Decision

### 1. One typed plan in `platform-domain`

Immutable records in `com.performance.platform.domain.injection`, with no
Spring, Jackson or Gatling type:

```java
public record WorkloadPlan(ArrivalModel model, LoadScope scope, List<Stage> stages) {}

public enum ArrivalModel { OPEN, CLOSED }      // OPEN: users/s — CLOSED: concurrent users

public sealed interface Stage permits Ramp, Hold, Burst, Pause {
    boolean measured();                         // false = warm-up, excluded from metrics (ADR-068)
}
public record Ramp(double from, double to, Duration duration, boolean measured) implements Stage {}
public record Hold(double level, Duration duration, boolean measured) implements Stage {}
public record Burst(long users, boolean measured) implements Stage {}   // OPEN only
public record Pause(Duration duration, boolean measured) implements Stage {}
```

The unit is derived from the model and never declared separately: `OPEN`
levels are arrivals per second, `CLOSED` levels are concurrent users.
`LoadScope` is defined by ADR-067.

### 2. YAML: `workload`, inline in the step

```yaml
parameters:
  workload:
    model: open
    stages:
      - ramp:  { from: 0, to: 200, duration: 30s, measured: false }
      - hold:  { level: 200, duration: 5m }
      - burst: { users: 500 }
      - pause: { duration: 10s }
```

The plan stays inline in the step (ADR-053). No root-level registry is
reintroduced.

### 3. The eight existing types become presets

`loadModel: { type, parameters }` remains valid YAML. `platform-scenario-dsl`
expands it into a `WorkloadPlan` **before validation**; `CUSTOM` expands into
one `Ramp` per pair of points. After parsing, only the plan exists:
`LoadModel`, `LoadModelType` and the per-type branches of the translator are
deleted. A step declares `workload` or `loadModel`, never both.

### 4. Validation at parse time

`DefaultScenarioValidator` rejects (HTTP 400), before any dispatch: empty
plan, non-positive duration, negative level, `Burst` in a `CLOSED` plan,
a plan with no measured stage, and unknown stage kinds. Engine-specific limits
are checked by the adapter (ADR-065).

### 5. Time semantics

- **Injection window** = sum of stage durations. This is what the plan defines.
- **Drain** = time allowed for in-flight requests after the last stage; bounded
  by the step `timeout`, never part of the plan.
- Measurements are computed on measured stages only (ADR-068).

### 6. The resolved plan is evidence

The expanded plan and its SHA-256 hash (canonical serialisation) are stored
with the execution and shown in the report next to the injection results
(ADR-044, ADR-051). Two runs are comparable only if their plan hashes match.

### 7. One translator, from the plan

`LoadModelTranslator` is replaced by a translator `WorkloadPlan -> Gatling
injection`, with two outputs (open and closed injection steps). It lives in
`platform-injection-gatling` and is the only place that knows Gatling types.

## Consequences

- A new profile shape is a composition of stages in YAML, not code.
- The closed model becomes expressible; it is delivered after the open model.
- The platform can validate, split, hash and report a plan without Gatling.
- Existing scenarios keep working through presets; their parameter errors move
  from run time to parse time.
- `platform-domain` gains five records and two enums and loses `LoadModel` and
  `LoadModelType`. `interfaces-registry.md`, Spec 01 and Spec 06 are updated
  on acceptance, with `architecture.md` (ADR-061).

## Rejected alternatives

- **Keep adding enum types.** Every new shape stays a code change and the plan
  stays invisible to the platform.
- **Wait for a second engine before owning the model.** The need is not a
  second engine; it is splitting, validating and recording the plan today.
- **A request-rate model.** Gatling has no faithful request-rate control for
  multi-request simulations; see ADR-065.

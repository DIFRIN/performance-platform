# ADR-067 - Grant share and global load scope

**Status** : PROPOSED (2026-11-16)
**Related** : ADR-045 (execution boundaries), ADR-055 (fan-out), ADR-058 (single orchestrator), ADR-064, ADR-065, ADR-068, ADR-069

## Context

- With `fanOut: all`, every granted agent receives the same step and therefore
  the same profile (ADR-055). Total load is `profile x number of agents`: it
  depends on how many agents happened to claim, not on the scenario.
- A scenario cannot state "20 000 users/s in total".
- `execution/` must not know performance policy (ADR-045): the orchestrator
  cannot compute per-agent rates itself.
- For `fanOut: all`, grants are issued together after the claim window is
  settled (`TransportStepDispatcher`), so the number of granted agents is
  known when grants are sent.

## Decision

### 1. The grant carries a generic share

`TaskGrantSignal` gains one field:

```java
public record GrantShare(int index, int count, double fraction) {}
// index: 0-based position of this agent among the granted agents
// count: number of granted agents
// fraction: part of the whole assigned to this agent; fractions sum to 1.0
```

- `fanOut: one` -> `GrantShare(0, 1, 1.0)`.
- `fanOut: all` -> agents sorted by `AgentId`, equal fractions `1 / count`.
- The orchestrator computes nothing else. It has no notion of rate or load.

The share reaches the executor as a read-only attribute of `ExecutionContext`.
`TaskExecutor` keeps its signature. Any executor may use the share (for
example to partition a dataset); the engine does not care how.

`fraction` is carried now so that capacity-weighted shares can be added later
without another contract change. Weighting itself is out of scope.

### 2. `scope` on the workload plan

```yaml
workload:
  scope: global        # per-agent (default) | global
```

- `per-agent` — each agent runs the plan as written. Current behaviour.
- `global` — the plan describes the total; each agent scales it by its share.

Scaling is done **on the agent**, by the injection adapter:

- rates and levels are multiplied by `fraction`;
- integer quantities (`Burst.users`, closed-model users) are split by largest
  remainder using `index`, so the per-agent values sum exactly to the global
  value;
- an agent whose share of every stage is zero runs nothing and returns an
  empty contribution, which is not an error.

`scope: global` requires `fanOut: all` and an adapter that declares
`supportsGlobalScope` (ADR-065); otherwise the scenario or the step is rejected.

### 3. Minimum agents

```yaml
fanOut: all
minAgents: 4
```

If fewer agents are granted, the step fails **before** any grant is sent: no
load is generated. Default `1`.

### 4. Data partitioning uses the same share

Counters and feeders exposed to request templates (`{index}`) advance as
`index + n x count`, so agents produce disjoint values without coordination.
Seeds are derived from the plan hash (ADR-064) and `index`.

### 5. Agent loss during injection

```yaml
onAgentLoss: abort     # abort (default) | continue
```

- `abort` — the step fails and the remaining agents are cancelled (ADR-066).
- `continue` — surviving agents keep their share. Delivered load drops; the
  run is marked by the integrity checks of ADR-069.

There is **no reallocation**: a running Gatling injection profile cannot be
changed, and replaying missed load would create an unplanned burst.

### 6. Start coordination: record, do not synchronise

No readiness barrier and no clock-skew abort. Each agent starts on grant and
records its actual start and end instants; aggregation aligns time windows
(ADR-068). Start skew is reported as a metric. A barrier is reconsidered only
if measured skew is shown to distort results.

## Consequences

- Total load is defined by the scenario and no longer by the number of agents.
- The orchestrator stays generic: one small value object on an existing signal.
- `TaskGrantSignal` changes: transport message set (Spec 05 section 3), signal
  codecs and agent runtime (Spec 04) are updated. Orchestrator and agents must
  be upgraded together.
- `StepDefinition` gains `minAgents` and `onAgentLoss`; `platform-scenario-dsl`
  validates them.
- Existing scenarios are unaffected: default scope is `per-agent`.
- Equal shares assume comparable agents; heterogeneous fleets wait for
  weighting.

## Rejected alternatives

- **Orchestrator computes per-agent plans.** Puts performance policy in
  `execution/`, against ADR-045.
- **A pluggable step-partitioner SPI in the engine.** More general, but one
  value object covers the known needs with far less surface.
- **Prepare/start barrier with synchronised clocks.** A two-phase step protocol
  and a clock dependency, for skew that is small against ramps of minutes.
- **Reallocate on agent loss.** Not implementable on a running Gatling
  simulation and changes the experiment mid-run.

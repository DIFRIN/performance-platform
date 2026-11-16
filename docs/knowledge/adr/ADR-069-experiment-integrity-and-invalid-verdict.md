# ADR-069 - Experiment integrity checks and the INVALID verdict

**Status** : PROPOSED (2026-11-16)
**Related** : ADR-021 (CLI exit codes), ADR-044 (report validation), ADR-049 (assertion model), ADR-065, ADR-067, ADR-068

## Context

- `Verdict` has three values: `SUCCESS`, `WARNING`, `FAILED`.
  `VerdictCalculator` derives it from assertion statuses only.
- A run where the generator delivered half the planned load, or lost an agent
  under `onAgentLoss: continue` (ADR-067), can pass every latency threshold. It
  is reported as `SUCCESS` although the system was never tested at target load.
- `FAILED` would be equally wrong: the system under test did not fail; the
  experiment did.
- `AssertionStatus.INCONCLUSIVE` exists for a single check without data, and
  leads to "human review required" in the report. There is no run-level
  equivalent.

## Decision

### 1. A fourth verdict

`Verdict` gains `INVALID`: the run did not apply the experiment it describes,
so its assertions say nothing about the system under test.

Precedence: `INVALID` > `FAILED` > `WARNING` > `SUCCESS`.

### 2. Integrity checks, produced automatically

Every injection step yields checks in a new assertion family `integrity`,
evaluated by the existing assertion pipeline (ADR-049) from the metrics of
ADR-068. No scenario declaration is needed.

| Check | Condition for failure | Default |
|---|---|---|
| Arrival deviation | `injection.arrival.deviation` above tolerance | 5 % |
| Agent loss | an agent granted for the step did not return a result | any |
| Incomplete run | the worker was stopped before the plan ended (ADR-066) | any |
| Generator saturation | `injection.generator.cpu` above threshold | 90 % — warning only |

A failed integrity check makes the verdict `INVALID`. Generator saturation
alone yields `WARNING`: high CPU is a signal, not proof of under-delivery; the
arrival deviation is the proof.

### 3. Tolerances are configurable per step

```yaml
integrity:
  maxArrivalDeviation: 5%
  maxGeneratorCpu: 90%
```

Values are validated at parse time. An integrity check cannot be disabled; its
tolerance can be widened, and the value used is shown in the report.

### 4. Not applicable cases

- Opaque custom simulations (ADR-065): arrival deviation is `INCONCLUSIVE`,
  which keeps the existing "human review required" behaviour, not `INVALID`.
- A step that fails before generating load (rejected plan, `minAgents` not
  met) is a step failure: the execution is `FAILED` as today, not `INVALID`.

### 5. Exposure

- **Report**: the verdict banner states `INVALID` with the failed integrity
  checks first; assertion results are shown but marked as not conclusive.
- **API / UI**: `INVALID` is a distinct verdict value and badge.
- **CLI** (ADR-021): `INVALID` exits with a non-zero code, distinct from
  success. Proposed: the same code as an execution error (`2`), since the run
  must be redone, not the system fixed.
- `ExecutionStatus` is unchanged: the execution `COMPLETED`; only the verdict
  is `INVALID`.

## Consequences

- An under-delivered or degraded run can no longer be read as a pass or as a
  system failure.
- CI pipelines can distinguish "system too slow" from "test not valid".
- `Verdict` (domain), `VerdictCalculator`, the report templates, the REST DTOs
  and the UI badges change. Consumers switching on `Verdict` must handle the
  new value.
- Runs on undersized generators will start reporting `INVALID` where they
  reported `SUCCESS`; this is the intended correction.

## Rejected alternatives

- **Reuse `FAILED`.** Blames the system under test for a generator problem.
- **Reuse `WARNING`.** Easy to ignore; the run still looks usable.
- **Leave it to scenario authors** to assert on arrival deviation. The check
  that matters most would be missing exactly when nobody thought of it.

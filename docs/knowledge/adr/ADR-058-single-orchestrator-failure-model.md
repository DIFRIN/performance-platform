# ADR-058 - Single orchestrator and explicit failure model

**Status** : ACCEPTED (2026-11-15)

## Context

The orchestrator's behaviour on failure is implicit and partly unfinished:

- execution state is persisted per phase, but a restarted orchestrator does not
  resume an interrupted execution (Spec 02 section 9);
- `work-in-progress-reset-interval` is defined and transmitted in
  `ExecutionConfig` but not used by the dispatcher (Spec 02 section 8);
- execution groups are coordinated inside one orchestrator instance only
  (ADR-046).

A run interrupted by a restart can therefore stay `RUNNING` forever in the read
model, and nothing states whether two orchestrators may run side by side.

## Decision

- **Exactly one orchestrator instance** per platform deployment. This is a
  stated constraint, not an accident. Running two is unsupported.
- **An orchestrator restart fails in-flight executions.** At startup, every
  execution found in a non-terminal state is marked `FAILED` with the reason
  `orchestrator restarted`, its execution group is released, and
  `ScenarioFinished` is published.
- Agents that complete a task whose execution is already terminal have their
  result logged and discarded.
- `work-in-progress-reset-interval` is removed from `ExecutionConfig` and from
  the documented properties. `TaskWorkInProgress` remains a progress signal for
  the read model only; the only liveness mechanisms are
  `task-execution-timeout` and agent loss (ADR-033).
- Resume after restart and orchestrator high availability are out of scope.
  They require a new ADR backed by a demonstrated need.

## Consequences

No execution can remain `RUNNING` after a restart; the UI, CLI and scheduler
(ADR-048) always see a terminal state. Single-instance coordination (execution
groups, claim granting of ADR-055) is correct by construction, with no
distributed lock. A long soak test is lost if the orchestrator restarts: this
is an accepted limit and must be written in the deployment spec (Spec 09). One
unused configuration property disappears.

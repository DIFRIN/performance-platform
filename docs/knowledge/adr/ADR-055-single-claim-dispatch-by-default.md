# ADR-055 - Single-claim dispatch by default, explicit fan-out

**Status** : ACCEPTED (2026-11-15)
**Supersedes** : ADR-011 (partially), ADR-034

## Context

A task is broadcast and the orchestrator accepts every claim and tracks each
execution (ADR-011). With the default `completion-policy: FIRST_COMPLETE`, the
orchestrator moves on after the first result, but every claiming agent still
executes the task. A side-effecting step (`database PURGE`, `POPULATE`,
`kafka-producer`) therefore runs once per capable agent. Running on all agents
is the right behaviour for load injection only.

The multi-claim model also forces two global policies (`completion-policy`,
`result-selection-policy`, ADR-034) and a `context[taskId][agentId]` nesting on
every consumer, including the common single-agent case.

## Decision

- Each step has a `fanOut`: `one` (default) or `all`.
  - `one` — the orchestrator grants the first claim and rejects the others; a
    rejected agent does not execute. The result is stored under
    `context[taskId]`.
  - `all` — every claim received within the claim window is granted; the step
    completes when all granted agents have answered or timed out. Results are
    stored per agent and reduced by the rule declared per metric (ADR-049).
- Granting is done by the single orchestrator (ADR-058): no distributed lock is
  needed. The claim/grant exchange uses the existing transport ports; an agent
  executes only after a grant.
- `completion-policy` and `result-selection-policy` are removed. `fanOut` is
  the only knob and it is per step.
- Routing stays by task name against `agent.supported-tasks` (ADR-015).

## Consequences

Preparation and assertion steps run exactly once. Injection declares
`fanOut: all` explicitly, so multi-agent load is a visible choice in the
scenario. Consumers of the context no longer handle an agent-keyed map unless
they asked for one. The dispatch protocol gains a grant message: this changes
the transport message set (Spec 05 section 3) and the agent runtime (Spec 04).
One extra round trip is added before execution; it happens before the measured
window. `TaskCorrelationTracker` keeps tracing every claim, granted or not.

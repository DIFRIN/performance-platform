# ADR-059 - LOCAL mode is the distributed engine with an in-memory transport

**Status** : ACCEPTED (2026-11-15)

## Context

ADR-005 promises one artifact and two modes. The engine nevertheless has
mode-specific paths (Spec 02):

- step outcome events are published by the engine in LOCAL only;
- lifecycle signals go through `LocalLifecycleDispatcher` (direct call) or
  `RemoteLifecycleDispatcher` (signal channel);
- dispatch timeouts and completion handling apply in DISTRIBUTED only, LOCAL
  being synchronous.

Each branch is a place where the two modes can diverge, which is why a
dedicated equivalence test (`LocalDistributedEquivalenceIT`) is needed.

## Decision

- LOCAL is defined as: the orchestrator, **one embedded agent** supporting all
  tasks, and the **in-memory transport**, in one process.
- The engine has a single execution path. It always dispatches through the
  transport ports, always receives results and outcome events from the agent
  runtime, and always sends lifecycle signals through the signal channel.
- `LocalLifecycleDispatcher` and the LOCAL-only outcome publication are
  removed. No engine or agent-runtime class reads the runtime mode; the mode is
  consumed only by the composition root (ADR-026) to choose beans.
- Timeouts, claim/grant (ADR-055) and agent loss apply identically in LOCAL.
- The in-memory transport delivers on virtual threads; it does not call the
  receiver on the sender's thread.

## Consequences

Equivalence between modes holds by construction; the equivalence test becomes a
regression guard instead of the only proof. A scenario validated in LOCAL
exercises the same dispatch, context and lifecycle code as in production.
LOCAL executions pay a small in-process messaging overhead, outside the
measured injection path. Failure cases only reachable in DISTRIBUTED today
(timeouts, no competent agent) become testable without containers.

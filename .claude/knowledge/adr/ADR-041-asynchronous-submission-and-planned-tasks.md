# ADR-041 - Asynchronous submission and persisted task visibility

## Context

Web submission called the blocking execution use case, delaying HTTP 202 and the
existing UI redirect until all phases finished. Task APIs only exposed results:
running and pending tasks were invisible.

## Decision

- Add the framework-free `SubmitScenarioUseCase`: persist and initialize an execution,
  launch its run on a virtual thread, then return its ID without waiting for completion.
  Web endpoints use this port. `ExecuteScenarioUseCase` remains synchronous for CLI.
- Persist an immutable ordered list of `ExecutionTask` metadata in `ExecutionState`:
  ID, executor name, phase and dispatch-started flag. No protocol-specific behavior or
  Spring dependency belongs in these types. ADR-043 adds a generic configuration
  snapshot for inspection only.
- The engine persists task starts before dispatch, and local terminal results are
  published as each dispatched task finishes, rather than only at phase completion.
  Dependency-skipped outcomes are still published by the phase executor.
  Serialize updates per execution to avoid lost starts for concurrent tasks.
- Store metadata in infrastructure JSONB in the initial schema (disposable database).
  The tasks endpoint merges
  this plan with persisted results to include PENDING/RUNNING tasks and their phases.
  Existing multi-agent terminal results remain visible individually.
- Legacy executions have an empty plan and retain result-only listing. Their
  original scenario cannot be inferred safely from results.

## Consequences

The UI can redirect after acceptance and poll immediately while work is in flight.
Submission errors before scheduling remain synchronous. New executions preserve
planned metadata across database reloads; this is not durable job resumption.
The existing progress API still groups pending and running tasks in its running
counter; individual task statuses distinguish them.

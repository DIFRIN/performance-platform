# ADR-043 - Execution inspection snapshots

## Context

The execution UI needs technical step configuration before completion, per-agent
results after completion, and the original submitted YAML. Task outcomes alone
cannot reconstruct either the source or a pending step.

## Decision

- The DSL parser retains the exact input in `ScenarioDefinition.sourceYaml`.
  This nullable string is provenance, not a second executable definition.
  Programmatically built scenarios may have no source.
- The engine copies that source into `ExecutionState.scenarioYaml` and creates a
  generic `ExecutionTask.configuration` snapshot: parameters, dependencies,
  required contexts, timeout, retry policy and any referenced load model.
  The snapshot does not select protocols or configure execution; infrastructure
  remains responsible for technology-specific behavior.
- Infrastructure persists source as TEXT and configuration inside the existing
  tasks JSONB column. Every state transition preserves both snapshots.
  As requested for the disposable environment, update the complete initial
  schema rather than adding an ALTER migration.
- The task API adds configuration, outputs, agent ID, duration in milliseconds
  and completion time, retaining separate rows for each agent.
- `GET /api/v1/executions/{id}/scenario` returns `{ "yaml": ... }`. A missing
  execution returns 404; source unavailable on a known execution returns null.
  The UI loads source only when expanded, as text rather than HTML.

## Consequences

Existing constructors remain usable with empty metadata or absent source.
Older executions cannot recover information that was not retained.
Configuration is available for pending/running steps; actual request counts and
other metrics appear when executors publish their results, not as invented live
metrics. API access to source and configuration exposes the submitted values,
including any inline credentials: scenario authors should use external references.

# ADR-053 - Scenario model simplification

**Status** : ACCEPTED (2026-11-15)

## Context

The scenario YAML repeats information the platform already knows:

- `requiredContexts` duplicates `dependsOn` on every step of every example.
- Cross-phase `dependsOn` (an INJECTION step depending on a PREPARATION step)
  adds nothing: phases run in strict sequence (Spec 02).
- `execution.mode` is in the scenario although the mode is a runtime property
  (`runtime.mode`, ADR-005). The `-local` and `-distributed` example files differ
  only by id, name, tags, mode and volumes.
- `linkedTo` and `refTaskId` are interpreted by the engine or the validator but
  live in the free-form `parameters` map, which is meant to belong to the task.
- Load models exist in two forms: a top-level named `loadModels` registry
  (Spec 01) and an inline `loadModel` in the step (example 02).

## Decision

- `requiredContexts` becomes optional and defaults to the step's `dependsOn`
  plus its `ref`. It stays available as an explicit override (ADR-010 is kept:
  the agent still receives a partial context).
- `dependsOn` orders steps **inside a phase** only. A reference to a step of an
  earlier phase is accepted and ignored for ordering; a reference to a later
  phase is a validation error.
- `execution.mode` is removed from the scenario. `ScenarioDefinition.executionMode`
  is deleted; the mode comes from the runtime only. One scenario file runs in
  both modes.
- `ref` becomes a first-class step field (`StepDefinition.ref`, a `TaskId`)
  replacing `parameters.refTaskId`. `parameters.linkedTo` is replaced by
  `during` (ADR-054). `parameters` is opaque to the engine.
- The inline `loadModel` is the only form. The top-level `loadModels` map,
  `ScenarioDefinition.loadModels` and `LoadModelRegistry` are removed.
- The secondary constructors of `ScenarioDefinition` are removed.
- No compatibility alias: the platform is not in production; examples, specs and
  tests migrate with the code. Removed keys are rejected by the strict parser.

## Consequences

A step is `id`, `task`, `phase`, `parameters` and, only when needed,
`dependsOn`, `ref`, `timeout`, `retryPolicy`. Example scenarios lose roughly a
third of their lines and the local/distributed pairs collapse into one file.
`StepDefinition` and `ScenarioDefinition` are public domain records: this ADR
is the required authorisation for the change. Spec 01 sections 2, 5 and 6 must
be rewritten. Volumes that differed between local and distributed examples
become a parameter of the run, not of the file.

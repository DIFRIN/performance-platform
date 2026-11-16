# ADR-054 - Service steps and reactive behaviour

**Status** : ACCEPTED (2026-11-15)

## Context

Some tasks do not run to completion; they must be alive while something else
runs: a mock server receiving the SUT's calls, a Kafka monitor, a sampling
assertion. Today this is expressed three different ways:

- two paired steps (`mock-server` `action: START` in PREPARATION and
  `action: STOP` in ASSERTION) that the author must keep consistent;
- a `shell sleep` step in the ASSERTION phase to let the SUT drain;
- interval assertions, detected through `parameters.linkedTo`, which receive a
  `START` signal before INJECTION and `STOP` after (Spec 02 section 3).

Only the third is modelled by the engine. A related case has no model at all:
an agent that receives a request and must then send one (callback, forward).

## Decision

- Two kinds of step exist, and the difference is explicit:
  - **action step** — runs to completion (current behaviour);
  - **service step** — has a lifetime: started, kept alive, stopped.
- A step is a service step when it declares `during`:
  - `during: injection` — START before the INJECTION phase, STOP after it;
  - `during: scenario` — START before PREPARATION, STOP after ASSERTION.
- `drain: <duration>` (optional) delays the STOP after the scope ends. It
  replaces `shell sleep` steps.
- A service step publishes its `TaskResult` **at STOP**, under its own id, like
  any other step. Assertions read it through `ref` (ADR-053). The service does
  not need to stay alive during the ASSERTION phase.
- The existing lifecycle signals (`START`/`STOP`, local and remote dispatchers)
  are reused unchanged. `assertionIntervalSteps` and `parameters.linkedTo` are
  replaced by this single mechanism. `action: START|STOP` parameters are removed
  from `mock-server`.
- `StepDefinition` gains `during` and `drain`. `phase` is not declared on a
  service step.
- **Rule: the orchestrator coordinates lifetimes, never individual messages.**
  Per-request cause and effect (receive, then send) stays inside one executor
  on one agent.
  - HTTP: the reaction is declared in the mock mapping (callback / proxy
    capabilities of the embedded mock). It is one `mock-server` service step;
    outbound calls are part of its result.
  - Non-HTTP trigger: a generic `responder` service task (`on:` trigger,
    `then:` action) may be added when a real case exists. It is not part of
    this ADR's scope.

## Consequences

Example 01 goes from 8 steps to 6 and no START/STOP pair can be mismatched. A
failed or cancelled run still stops services, because STOP is owned by the
engine rather than by a later step that may be skipped. The measurement path is
never routed through the orchestrator, so transport latency cannot pollute
results. `StepDefinition` is a public record: this ADR authorises the change.
Specs 01, 02 (sections 3 and 5) and 04 must be updated. An agent that hosts a
service step must hold state between START and STOP; agent loss during the
scope fails the step (ADR-033 applies).

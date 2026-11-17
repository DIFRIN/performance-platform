# ADR-070 - Weighted request mix; no journey DSL in YAML

**Status** : ACCEPTED (2026-11-17)
**Related** : ADR-039 (dynamic request values), ADR-064, ADR-065, ADR-068

## Context

- The generated HTTP simulation sends exactly one request per virtual user
  (`HttpScenarioSimulation`). A step therefore tests one endpoint.
- Testing a realistic mix (70 % writes, 30 % reads) needs several injection
  steps with hand-computed rates, or a custom Java simulation.
- A full journey language in YAML (chaining, branching, extraction,
  correlation, pauses) would duplicate the Gatling Java DSL, which the platform
  already supports through custom simulations.
- With one request per user, arrivals per second equal requests per second.
  This property keeps the offered-load accounting of ADR-068 exact.

## Decision

### 1. A step may declare a weighted mix of single requests

```yaml
parameters:
  target: sut-api
  workload: { ... }              # ADR-064
  requests:
    - name: create
      weight: 70
      path: submit-event
      method: POST
    - name: read
      weight: 30
      path: get-event
      method: GET
```

- Each virtual user sends **one** request, chosen by weight.
- Weights are positive numbers; they are normalised, not required to sum to 100.
- Each request uses the existing request specification and dynamic values
  (ADR-039). The current single-request form stays valid and equals a mix of one.
- The choice is seeded from the plan hash and the grant index, so a run is
  reproducible.
- The same structure applies to the generated Kafka simulation (weighted
  message templates).

### 2. Results are reported per request name

Counters, histograms and windows (ADR-068) are kept per request name and for
the total. Assertions may target `injection.latency.p95` (total) or a named
request. The achieved mix is reported next to the declared weights.

### 3. No journey DSL

YAML does not gain chaining, branching, extraction, correlation, think time or
loops. A multi-request journey is written as a custom Java simulation, under
the rules of ADR-065 (declared capabilities or opaque reporting).

This limit is revisited only on a concrete use case that custom simulations
cannot serve.

## Consequences

- Realistic endpoint mixes are configurable without code and without several
  coordinated steps.
- Arrivals and requests stay one-to-one for generated simulations, so planned
  versus achieved load remains exact.
- The YAML surface stays small; there is one way to express complex journeys.
- `HttpScenarioSimulation`, its request specification and the scenario
  validator change; Spec 01 and Spec 06 are updated on acceptance.

## Rejected alternatives

- **Full journey DSL in YAML.** Large surface, permanent lag behind Gatling,
  and it breaks the one-arrival-one-request property.
- **Several injection steps for a mix.** Rates must be computed by hand and
  results are split across steps.

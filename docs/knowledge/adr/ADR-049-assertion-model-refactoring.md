# ADR-049 - Assertion model: measurement, expectation and verdict as separate concerns

**Status** : ACCEPTED  
**Date** : 2026-11-14

> **Note d'implémentation (2026-11-14)** : le modèle (`Observation`,
> `Expectation`, `Check`), le pipeline (`ParameterBinder`, `OperatorResolver`,
> `CheckEvaluator`, `TemporalCheckEvaluator`, `AssertionSummaryFactory`), les
> deux familles, la validation parse-time et la suppression des six anciens
> executors sont livrés. Deux points restent ouverts : `ReportDefinition.criteria()`
> est encore évalué directement par `PerformanceAssessmentBuilder` (pas encore
> traduit en `AssertionSpec`), et `DescriptionFormatter` n'existe pas — le
> libellé est produit par `AssertionSummaryFactory`.

## Context

Six assertion adapters (`gatling-metric`, `database-assertion`, `kafka`,
`wiremock`, `http-mock`, `file`) each re-implement parameter parsing, operator
resolution, error handling, description formatting and evidence assembly. The
same metric has three identities (`p95` in YAML, `p95Ms` in report criteria,
`mean` vs `average`), two `AssertionResult -> AssertionSummary` mappers produce
different output keys (`actualValue` vs `actual`), and the report rebuilds an
`AssertionResult` with `evidence = null`, losing the proof. `EQ` compares
`double` exactly, state checks (`EXISTS`, `CHECKSUM`) are forced through numeric
operators, and an unknown metric becomes a runtime `ERROR` instead of a
scenario validation error. `ReportDefinition.criteria()` forms a second,
unrelated judgement path with its own metric names.

Root cause: measuring, expecting and judging are conflated inside each adapter.

## Decision

- Split the model into four domain concepts, all framework-free:
  - `MetricDefinition(MetricId id, MetricKind kind, String unit, Reduction reduction, String description)`
    — a closed catalogue, not a stringly-typed map.
  - `Observation(MetricId, double value, String unit, windowStart, windowEnd, source, attributes)`
    — a measured fact, never a judgement.
  - `Expectation(AssertionOperator operator, double threshold, double tolerance, Severity severity)`
    — a typed expectation, with an explicit tolerance.
  - `Check(MetricId, Observation, Expectation, CheckStatus, rationale)` — the
    single place where a verdict is computed.
  - `SloStatement(String title, String sli, String slo, String rationale)` — the
    textual, human-readable intent of the assertion. Carried unchanged into
    `AssertionSummary` and the report.
- One assertion produces **N checks**. `AssertionSummary` carries
  `slo` (the `SloStatement`), `family`, `checks[]`, a rollup rule
  (`ALL`, `ANY`, `QUORUM`) and the existing
  `history`/`evaluationDuration`/`evaluatedAt`.
- The textual SLI/SLO is **documentation with contractual value, never a
  threshold source**. Thresholds stay explicit per check; the text cannot be
  parsed into a rule, and a check without explicit threshold is a validation
  error even when the text contains a number.
- Each check carries a short `intent` (default: generated from
  operator/threshold/unit, overridable) so a report line is understandable
  without reading the YAML.
- Add `INCONCLUSIVE` to `AssertionStatus`: a missing or insufficient measurement
  is neither `PASSED`, `FAILED`, `SKIPPED` nor a technical `ERROR`. Public enum
  change, hence this ADR.
- `EQ` on floating point requires a strictly positive `tolerance`; the validator
  rejects `EQ` with `tolerance == 0` for `GAUGE`, `RATIO` and `QUANTILE` metrics.
- Two declarative families map onto one pipeline:
  - `performance-thresholds` — expands `latency: {p50, p95, p99}`, `errorRate`,
    `throughput` into normalised checks with per-check severity.
  - `direct-assertion` — volumetry, row counts, update counts, matched calls,
    file state; resolves a `MetricId` and applies one `Expectation`.
- Existing task names are **removed**: the two families are the only assertion
  task names. The platform is not yet in production, so no compatibility alias
  is introduced and the six legacy executors are deleted rather than adapted.
- Extract the duplicated plumbing into `platform-assertion` (non-Spring):
  `ParameterBinder`, `OperatorResolver`, `CheckEvaluator`, `AssertionSummaryFactory`,
  `DescriptionFormatter`. Adapters keep only the measurement knowledge.
- Keep exactly **one** mapper `Check -> TaskResult`; delete the duplicate
  implementation in `platform-plugin-api`.
- Move validation to parse time (`DefaultScenarioValidator`): unknown metric,
  incompatible unit, unknown `refTaskId`, `EQ` without tolerance, threshold
  outside the metric kind's domain are scenario errors (HTTP 400), not runtime
  `ERROR`.
- `ReportDefinition.criteria()` is reduced to sugar that produces an
  `AssertionSpec`; it is no longer a second evaluation engine. Metric names are
  the canonical `MetricId` values.

## Consequences

Adapters lose most of their code; behaviour becomes uniform and auditable.
`INCONCLUSIVE` changes the meaning of an empty measurement from `FAILED` to an
explicit report limitation, so a scenario with a broken metric no longer fails
silently or catastrophically. There is no compatibility layer: one assertion
model, one set of task names, one mapper, and the examples and documentation
are migrated with the code. Multi-agent aggregation stops being
implicit: the reduction rule is declared per metric (percentiles use the worst
agent, counters sum, states require all). The report consumes `checks[]`, so a
verdict is explainable from one structure instead of three.

The textual SLI/SLO travels with the execution and appears in the report next to
the checks it describes, which makes a campaign reviewable by someone who never
opened the YAML. It does not weaken validation: omitting a threshold is still an
error, even when the sentence states a number.

This ADR does not introduce external collectors; see ADR-050 for panel-backed
metric sources and ADR-051 for report evidence.

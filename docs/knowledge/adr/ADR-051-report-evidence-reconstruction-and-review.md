# ADR-051 - Report evidence: reconstructed series, optional snapshots and attributed human review

**Status** : ACCEPTED  
**Date** : 2026-11-14

> **Note d'implémentation (2026-11-14)** : `AssertionSummary.slo`, la section
> SLI/SLO du rapport, `reviewState = REQUIRED` calculé par le rapport et les
> preuves issues de `Check.observed` sont livrés. Le rendu SVG dans le
> renderer HTML/PDF (aujourd'hui disponible via le CLI `render`), la baseline
> visuelle et l'API/IHM de revue humaine restent à brancher.

## Context

ADR-044 allowed dashboard evidence as a link or a bounded PNG data URI, and
stated that a graph alone is not verifiable. ADR-050 makes the underlying data
queryable and turns it into `Observation`/`Check`. Two questions remain: what the
report displays, and what happens when a panel cannot be replayed.

Three artefacts are possible for the same panel: an external link, a captured
PNG, or a chart rebuilt by the platform from the fetched series. They do not have
the same evidentiary value: a link can rot or change, a PNG is opaque and
non-reproducible, a rebuilt chart is derived from data the platform itself holds
and can be regenerated identically.

## Decision

- **Primary evidence is the reconstructed series.** Reporting renders the
  persisted observations (time, value, unit, resolution, query fingerprint) into
  a deterministic server-side chart (SVG for HTML/PDF, series array for JSON).
  The chart is a *view* of the same data used by the checks, so a reader can
  reconcile verdict and curve.
- **Snapshots remain supported, as optional attestation.** A scenario may attach
  a PNG data URI or a link for what the operator actually saw. It is labelled
  "snapshot — human attestation" and never contributes to a check. Bounds from
  ADR-044 are kept (PNG only, size and dimension limits, no remote fetch by the
  renderer).
- Extend `ReportDefinition.Graph` compatibly: `kind` (`SERIES` | `SNAPSHOT` |
  `LINK`), optional `seriesRef` pointing to a declared panel series, and
  `caption`/`source`. An absent `kind` defaults to `LINK` so existing scenarios
  keep their meaning.
- A panel without resolvable queries produces no check. It is recorded as
  `INCONCLUSIVE` with reason `NO_QUERY` and sets the report into **review
  required**; the report verdict cannot be `SUCCESS`.
- Distinguish two states that must not be conflated:
  - check-level `INCONCLUSIVE` (data expected, unavailable or insufficient);
  - report-level `reviewState` (`NONE` | `REQUIRED` | `ACCEPTED` | `REJECTED`),
    owned by a human, not by an assertion.
  `INCONCLUSIVE` is an assertion concept (ADR-049); `PENDING_REVIEW`
  (`reviewState = REQUIRED`) is a **reporter responsibility** — it is computed
  by the report builder from the checks and the available evidence, never
  emitted by a plugin or an assertion executor.
- The report carries a dedicated **SLI/SLO section** built from
  `AssertionSummary.slo` (ADR-049): title, SLI text, SLO text, rationale,
  per-check intent and outcome. A reader can judge the pertinence of an
  assertion, not only its result.
- **Visual baseline is opt-in and report-only.** A configuration flag
  (`reporting.visual-baseline.enabled`, default `false`) enables comparison of
  `SNAPSHOT` graphs against a stored baseline, with a declared tolerance and
  directory. The result is an informational attribute of the snapshot
  (`CHANGED` | `UNCHANGED` | `NO_BASELINE` | `UNAVAILABLE`) displayed in the
  report. It is **never** a check, never changes `reviewState`, and never
  changes the verdict. When disabled, snapshots are displayed without any
  comparison.
- Human review is an **append-only, attributed record**: author, timestamp,
  decision, rationale, and the evidence identifiers consulted. It never mutates
  a check. A new execution gets a new review; there is no "last verdict wins".
- Verdict composition: `FAILED` if any critical check failed or technical
  failure occurred; otherwise `WARNING` if any check is `INCONCLUSIVE`/`SKIPPED`,
  if a limitation exists, or if review is `REQUIRED`; `SUCCESS` only when all
  applicable checks passed and no review is required. A human `REJECTED`
  decision downgrades the *published* verdict to `FAILED` with an explicit
  `override` field; `ACCEPTED` never upgrades a failed machine verdict.
- Review is exposed as a separate resource in the model
  (`PerformanceAssessment.review`, alongside `validation`) rather than as an
  assertion output, so it is auditable and cannot be forged by a plugin.

## Consequences

Reports stop being a gallery of screenshots and become reproducible evidence:
the curve and the numbers come from one dataset, and the snapshot is clearly
marked as testimony. Review required is visible in the verdict, which removes the
two failure modes of the previous state — a green report nobody checked, and a
human judgement silently contradicting objective metrics.

Costs: chart rendering must be deterministic (fixed scale rules, explicit units,
no client-side library in PDF); storing series increases report size, so
resolution and retention are declared per panel; and the review ledger needs an
owner and a retention policy. Copying a screenshot into the report is no longer
sufficient to close a campaign - by design.

Visual baseline adds a maintenance cost (baseline refresh, tolerance tuning) for
a purely cosmetic signal, hence its default-off configuration: it protects the
report appearance without ever influencing a verdict.

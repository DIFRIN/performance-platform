# ADR-044 - Evidence-based performance reports

## Context

Reports previously used a placeholder scenario version, classified JSONB-restored
outputs as preparation data, omitted p95 from HTML/PDF, and treated absent checks
as success. HTML framing was blocked by the default X-Frame-Options DENY header.
The requested report has six sections and supports optional telemetry, including
external dashboard evidence or exported graphs.

## Decision

- Add a framework-free `ReportDefinition` under `scenario.report`. It describes
  application components/versions, environment, test objectives, load assumptions,
  numeric criteria, sourced observations, graph exports and editorial information.
  It does not configure a collector or introduce technologies into application.
- Persist `ScenarioDetails` alongside the executable state, including the report
  definition, original scenario name, version, tags and metadata. No DSL parsing
  dependency is introduced into reporting. The disposable database initial schema
  gains `scenario_details JSONB`.
- Build one `PerformanceAssessment` with context, load model, SLI/SLO checks,
  technical indicators, analysis and validation. JSON exposes it directly; HTML
  renders it; PDF renders that same HTML.
- Restore known typed outputs after JSONB reload inside reporting. Preserve all
  injection agents. Show their percentiles individually, never averaging them.
  A scoped percentile criterion uses the worst agent; throughput criteria use
  the lowest participating agent rate; error rates use summed request counts.
- Criteria compare measurements using an explicit operator and threshold.
  Unspecified severity defaults to critical. Failed critical checks, assertion
  errors and technical execution failures produce FAILED. Failed minor checks,
  skipped checks, missing required evidence or incomplete context produce WARNING.
  SUCCESS requires complete applicable evidence, duration/volume/error/saturation
  criteria, a latency or throughput SLO, and successful checks.
- Optional uncollected telemetry is explicitly marked and does not by itself
  prevent success. A graph alone is not machine-verifiable saturation evidence.
  Observations can reference numeric task outputs or sourced external values.
- Graphs may be absolute HTTP(S) links or bounded PNG data URIs. Renderers never
  fetch arbitrary remote resources or attempt to infer CPU/DB/network health.
  PNG exports are embedded in HTML/PDF and retained in JSON.
- Report responses allow SAMEORIGIN framing; all other responses retain DENY,
  including with security disabled. API authorization is unchanged. The iframe
  preserves same-origin framing and user-opened evidence links but disables scripts.
  Report polling stops on availability instead of reloading the iframe.

## Consequences

Old reports are not rewritten. New scenarios without report context produce
WARNING instead of unsupported performance approval. Existing assertion-only
`VerdictCalculator` remains a compatibility helper; final report validation is
the evidence-based assessment, not that helper. Test authors can distinguish
minor assertions via an explicit `assertion` criterion with `critical: false`.

No automatic telemetry agent or authenticated Grafana exporter is implied.
Existing/custom collectors supply task outputs; users may import observations
and PNG exports. Unmeasured metrics and bottleneck hypotheses remain explicit.

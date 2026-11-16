# ADR-050 - External observability metrics as first-class assertion sources

**Status** : ACCEPTED  
**Date** : 2026-11-14

> **Note d'implémentation (2026-11-14)** : l'annexe `tools/observability-probe`
> est livrée (`panel-assertion` + CLI `catalog`/`query`/`verify`/`render`/
> `install-plugin`). Les ports `MetricSource`/`PanelCatalog` et la configuration
> `platform.metric-sources.*` ne sont pas encore exposés par le core : le plugin
> se configure comme le CLI (variables d'environnement / `probe.yaml`). Le
> reactor reste sans dépendance Grafana/Kibana.

## Context

ADR-044 states that "no automatic telemetry agent or authenticated Grafana
exporter is implied" and that a graph is not machine-verifiable evidence. In
practice the most valuable SLO data (latency percentiles, saturation, Kafka lag,
business volumetry) lives in dashboards, not in task outputs. Reading an image
cannot produce a defensible verdict, but replaying the panel's own query can.

Grafana exposes `POST /api/ds/query` (arbitrary panel target, `from`/`to` in
epoch milliseconds) and `GET /api/dashboards/uid/:uid` (panel `targets[]` and
template variables). Kibana has no public "execute this panel" endpoint; the
portable path is saved objects (`/api/saved_objects/{dashboard,visualization,lens,search}`)
read for their query, then a direct Elasticsearch `_search`/ES|QL call, or
`/api/console/proxy` when only Kibana credentials are available. Both platforms
are therefore **queryable**, with provider-specific request shapes.

Constraints to preserve: the core must stay free of vendor HTTP clients and
credentials; the report/verdict generator must call a source without knowing
which vendor it is; secrets must never appear in scenario YAML.

## Decision

- Define two outgoing ports in `platform-use-cases`:
  - `MetricSource` — `supports(MetricSourceRef)`, `fetch(MetricSourceRef, TimeWindow) -> List<Observation>`
  - `PanelCatalog` — resolves a panel reference into its executable queries
    (Grafana `targets[]`, Kibana saved object query) and its identity/version.
  Both are derived from `Observation`/`TimeWindow` of ADR-049; no vendor type
  crosses the boundary.
- Represent a source declaratively (`PanelReference`): provider, dashboard/panel
  identifiers, dashboard version, datasource uid, template-variable bindings,
  declared `queries[]` with canonical `MetricId`, unit, resolution (`step` or
  `intervalMs`), and a no-data policy. A panel without `queries[]` is evidence
  only and cannot produce a verdict.
- Anchor every fetch on the execution timeline: `from = injection.start - margin`,
  `to = injection.end + margin`, both declared in the scenario, never "now".
  Grafana receives epoch milliseconds; Elasticsearch receives the same window
  on `@timestamp`. Resolution (`intervalMs`/`maxDataPoints` for Grafana,
  `fixed_interval` for ES) is fixed and persisted with the observation.
- Freshness gate: a source declares its expected data delay (scrape interval,
  index refresh). The fetch waits or returns `INCONCLUSIVE`; it never judges on
  a partially available window.
- Persist a query fingerprint with every observation: provider, dashboard/panel
  identity and version, resolved query text, window, resolution, datasource uid.
  A verdict must be replayable from the report alone.
- Deliver **both Kibana channels**, selected per source in configuration, with
  an explicit fallback:
  - `ES_DIRECT` — read the saved object, execute the query on Elasticsearch
    (`_search` or ES|QL). Preferred: full aggregate vocabulary, exact
    percentiles, no Kibana dependency at read time.
  - `CONSOLE_PROXY` — `POST /api/console/proxy` with `kbn-xsrf`. Used when only
    Kibana credentials/spaces are available; keeps authorisation aligned with
    what operators see in Dev Tools.
  The two modes produce the same `Observation`; the chosen mode is part of the
  query fingerprint so a replay is unambiguous.
- Credentials and endpoints come from platform configuration/environment
  (`platform.metric-sources.<name>.*`), never from scenario YAML. The report
  only references source and panel identifiers, never tokens.
- The vendor implementation is delivered as an **annex, not a platform module**:
  `tools/observability-probe` builds a plugin JAR (`@Assertion` /
  `MetricSourceFactory`) dropped into `platform.plugins.dir`, discovered by the
  existing `PluginRegistry`. The platform reactor keeps zero Grafana/Kibana
  dependency.
- The annex is **also a standalone executable**: the same artefact exposes a CLI
  (`query`, `catalog`, `verify`, `render`, `install-plugin`) usable without the
  platform, offline, for diagnosis, fixture generation and CI smoke checks. CLI
  output is the JSON `Observation`/`QueryFingerprint` shape, so a captured run
  can be replayed or attached to a report verbatim. Usage is documented in
  `docs/knowledge/tools/observability-probe.md`.
- Transparency rule: `DefaultReportEngine` and the assertion pipeline only see
  `MetricSource` beans. Adding Datadog, Kibana or Prometheus-direct later is a
  new adapter, never a change in the report or verdict code.
- Supersedes the "no automatic exporter" clause of ADR-044. Graphs remain
  non-verifiable on their own; the query behind a graph is verifiable.

## Consequences

Automatic verdicts become possible on real observability data with the exact
numbers an operator sees, without pixel interpretation. A dataset that cannot be
resolved to a query (legacy panel, screenshot-only export) falls back to human
review (ADR-051), not to a fabricated verdict. The annex is optional: deployments
without the plugin simply have no panel-backed metrics. Query replay depends on
datasource backend plugins (frontend-only plugins answer 400 in Grafana), on
template variables being bound explicitly, and on Grafana 13 `/apis` migration
(the legacy `/api` path stays available).

Downside accepted: dashboards are an external contract. A dashboard edit changes
the metric; therefore the version and query fingerprint are mandatory, and a
missing or changed dashboard yields `INCONCLUSIVE` with a dated reason rather
than a silent value. Running the annex autonomously means the same code path is
exercised outside the platform, which makes a "works in the report but not in
the tool" divergence immediately visible.

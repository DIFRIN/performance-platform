# ADR-068 - Mergeable injection measurements and offered-load accounting

**Status** : PROPOSED (2026-11-16)
**Related** : ADR-049 (assertion model, reductions), ADR-044/051 (report evidence), ADR-064, ADR-066, ADR-067, ADR-069

## Context

- `InjectionResult` carries totals and precomputed percentiles per agent.
  `SimulationLogResultParser` builds a millisecond latency histogram
  internally, computes the percentiles, then discards the histogram.
- With several agents, `MetricCatalog` reduces `injection.latency.pNN` with
  `Reduction.MAX`: the reported p95 is the worst agent's p95, not the p95 of
  all requests. `injection.throughput` uses `Reduction.MIN`, which is the
  slowest agent's rate, not the total.
- Nothing compares what was planned with what was actually sent. A saturated
  generator produces a normal-looking report for a load that was never applied.
- Warm-up cannot be excluded from the results.
- Agents start at slightly different instants (ADR-067), so totals over
  "the run" mix different time ranges.

## Decision

### 1. `InjectionResult` carries distributions and windows

Added to the result, in domain records without external types:

- `startedAt`, `endedAt` — actual instants on the agent.
- `LatencyHistogram` — sparse buckets `{upperBoundMs -> count}`, successful and
  failed requests separately. Resolution: 1 ms up to 1 s, then 2 significant
  digits. No new Maven dependency.
- `List<LoadWindow>` — fixed windows aligned on wall-clock boundaries
  (`injection.window`, default 5 s), each with `plannedArrivals`,
  `startedUsers`, `requests`, `failures`, `measured`.
- `GeneratorHealth` — CPU time, GC pause time, peak heap of the worker (ADR-066).

`plannedArrivals` is computed by the adapter from the agent's own share of the
plan (ADR-064, ADR-067). `measured` comes from the stage flag.

Size is bounded: a one-hour run is 720 windows and a few hundred buckets per
agent. No per-request event crosses the platform transport.

### 2. A new reduction: `MERGE`

`Reduction` gains `MERGE`: histograms are summed bucket by bucket, then the
quantile is computed on the merged distribution.

### 3. Metric catalogue changes

| Metric | Before | After |
|---|---|---|
| `injection.latency.pNN` | `MAX` (worst agent) | `MERGE` — true global percentile |
| `injection.latency.pNN.worst-agent` | — | `MAX` — the former value, under an explicit name |
| `injection.throughput` | `MIN` | `sum(requests) / measured common window` |
| `injection.error.rate` | `WEIGHTED` | unchanged: `sum(failures) / sum(requests)` |
| `injection.arrival.planned` | — | `SUM` of planned arrivals, measured windows |
| `injection.arrival.achieved` | — | `SUM` of started users, measured windows |
| `injection.arrival.deviation` | — | `abs(achieved - planned) / planned`, percent |
| `injection.start.skew` | — | `max(startedAt) - min(startedAt)` across agents |
| `injection.generator.cpu` | — | `MAX` across agents |
| `injection.generator.gc.pause` | — | `MAX` across agents |

The meaning of `injection.latency.pNN` changes for multi-agent runs. The
platform is not in production and single-agent results are identical, so no
alias is kept (same policy as ADR-049).

### 4. Only measured windows count

All latency, error and throughput metrics are computed on windows where
`measured` is true. Warm-up stages remain visible in the report timeline but
do not enter any assertion.

### 5. Common window across agents

Counters are summed window by window on wall-clock boundaries. Throughput and
arrival metrics use the windows covered by every contributing agent; leading
and trailing partial windows are reported but excluded.

### 6. What is not claimed

Gatling does not expose scheduled-versus-started arrival times. The platform
therefore reports **planned versus achieved arrivals per window** and
generator health, and does not report per-arrival scheduling delay or
late/dropped arrivals. An adapter able to provide them may add metrics later.
Opaque custom simulations (ADR-065) report these arrival metrics as
`INCONCLUSIVE`.

### 7. Report

The injection section shows the planned and achieved arrival curves, the
global percentiles, the worst-agent percentiles, per-agent contributions and
generator health. Histograms and windows are stored as report evidence
(ADR-051), so percentiles can be recomputed later.

## Consequences

- Multi-agent percentiles and throughput become statistically correct.
- Under-delivered load becomes visible and assertable (ADR-069).
- Warm-up no longer pollutes results.
- `InjectionResult`, `Reduction` and `MetricCatalog` change in
  `platform-domain`; result codecs, the assertion pipeline and the report
  renderer follow. Old stored results lack histograms: their global percentiles
  are shown as unavailable, not recomputed.
- The parser reads the simulation log once, as today; window bucketing is
  added to the same pass.

## Rejected alternatives

- **Average per-agent percentiles.** Statistically wrong.
- **Keep worst-agent as the only percentile.** Useful to spot a bad agent, but
  it is not the latency users experienced.
- **Add HdrHistogram.** A new dependency (`constraints.md`) for a need covered
  by a small sparse map.
- **Stream live metrics through the transport.** Per-request or per-second
  streaming loads the control plane; ADR-050 covers live observability.

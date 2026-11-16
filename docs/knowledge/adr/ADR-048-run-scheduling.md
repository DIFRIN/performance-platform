# ADR-048 - Run scheduling (web UI, CLI and configuration)

**Status** : ACCEPTED  
**Date** : 2026-11-15

## Context

Scenarios could only be executed immediately: the web UI exposed Run /
Rerun buttons and the headless CLI executed once and exited (ADR-021).
Users need to defer a run (one-shot) or repeat it (cron) without keeping a
client connected, and the same capability must be reachable from the UI,
the CLI and configuration.

## Decision

- The domain owns the scheduling vocabulary and nothing else:
  `ScheduleKind` (`IMMEDIATE`, `ONCE`, `CRON`), `ScheduleSpec`,
  `ScheduledRun`, `ScheduledRunStatus` and `ScheduledRunId` in
  `platform-domain`. No Spring, no cron parser.
- New incoming ports in `platform-use-cases`: `ScheduleScenarioUseCase`,
  `ListScheduledRunsUseCase`, `CancelScheduledRunUseCase`. New outgoing
  ports: `ScheduledRunRepository` and `ScheduleCalculator`.
- `ScheduleCalculator` (implemented by `SpringScheduleCalculator` in
  infrastructure) is the single expression grammar shared by the UI, the
  CLI and configuration: `now`/empty, `in 10m`/`in 1h30m`, an ISO-8601
  instant, a 5- or 6-field cron expression (optionally prefixed `cron:`).
  Invalid input raises `InvalidScheduleException` (HTTP 400).
- `ScheduleRunService` (campaign engine, LOCAL or DISTRIBUTED/ORCHESTRATOR)
  persists a `ScheduledRun` with its resolved next occurrence.
  `ScheduledRunDispatcher` polls persisted due runs every second on a
  virtual thread and submits the retained YAML through
  `SubmitScenarioUseCase`. Because the queue is the database, pending runs
  survive an orchestrator restart. Cron runs are rescheduled after each
  trigger; one-shot runs become `COMPLETED`.
- `POST /api/v1/schedules` accepts `{scenarioYaml, schedule}`: an immediate
  expression delegates to the existing submission path, a deferred one
  returns `SCHEDULED` plus `nextRunAt`. `GET` lists and `DELETE` cancels a
  pending run. `PUT /api/v1/schedules/{id}` reschedules a pending run and
  `POST /api/v1/schedules/{id}/run` triggers it immediately (ADR-052).
  Existing run endpoints keep working unchanged.
- The web UI shares one control (`assets/js/schedule.js`) with a preset
  dropdown (Now, In 15 minutes, In 1 hour, Tomorrow 09:00, Every day 02:00)
  plus an advanced mode; the resolved next run is previewed under the input
  and invalid expressions are rejected inline before any API call. In the
  catalog, scheduling is opt-in: each valid scenario exposes Run (immediate)
  and Schedule side by side, and only the opened editor creates a deferred
  run. The control also appears on Upload & run (optional field), executions
  Rerun and detail Rerun. Deferred runs are listed in a dedicated table with
  their next occurrence, timezone, reschedule/run-now/cancel actions.
- The CLI reads `--schedule=`, then `platform.schedule`, then the
  `SCHEDULE` environment variable (ADR-006 priority). A one-shot schedule
  waits in-process before executing; cron is rejected headlessly with exit
  code 2 because a run-and-exit process cannot own a recurrence.
- Persistence uses a new `scheduled_run` table (V2 migration).

## Consequences

Scheduling is expressed once and resolved identically in all access modes.
Deferred runs need a datasource and are therefore unavailable in AGENT
mode, where no execution is ever submitted anyway. Because the dispatcher
triggers the *next* cron occurrence only after the previous submission, a
long-running execution does not queue overlapping runs; missed occurrences
during downtime are not replayed.

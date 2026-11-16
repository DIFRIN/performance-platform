# ADR-052 - Web UI feedback, navigation state and schedule management

**Status** : ACCEPTED  
**Date** : 2026-11-15

## Context

The first web UI iteration (PDR-028/PDR-029) used blocking `alert()` and
`confirm()` dialogs for every outcome, re-rendered whole tables on a 3 s
poll (even in hidden tabs), kept filters in memory only, and offered a
free-text schedule input whose grammar was validated by the server. The
result was caret loss while typing, API errors surfaced as modal alerts,
lost filter context on refresh, and no signal for assistive technologies
on SPA navigation.

## Decision

### Feedback

- Successes and information are non-blocking toasts (`assets/js/ui.js`),
  action failures are shown both as a toast and inline next to the affected
  row (`row-error`), so the list never becomes the error message.
- Blocking confirmations are reserved for destructive actions (delete an
  execution, abort a running one). Cancelling an execution or a scheduled
  run is reversible enough to be immediate.
- Each view exposes distinct loading (skeleton rows), error (banner +
  Retry) and empty states; "no data" and "filtered out" are differentiated,
  the latter offering a Clear-filters action.

### Navigation and state

- Filters, search, sort and page size live in the hash query
  (`#/executions?status=active&scenario=x&q=...&sort=duration&dir=desc`).
  They are updated with `history.replaceState`, so they survive refresh and
  sharing without remounting the view (routes remain backward compatible).
- After a route change the router focuses the page heading and announces
  the page name through an `aria-live` region; the executions list and the
  execution detail announce result counts and status transitions.

### Polling

- Views share `createPoller`: it pauses while `document.visibilityState`
  is `hidden`, refreshes immediately when the tab becomes visible, and
  reports `{refreshing, lastUpdated}` for a global "updated HH:MM:SS"
  indicator. Polling never rebuilds rows while a table input has focus.

### Scheduling

- The shared `schedule.js` control resolves the expression client-side:
  preset dropdown, advanced text mode, inline validation and next-run
  preview using the same grammar as `SpringScheduleCalculator`.
- Scheduling is never implicit in the catalog: each valid scenario shows
  **Run** (immediate) and **Schedule** side by side; the schedule editor is
  opened on demand for that row only, and its presets start at a deferred
  value (no "Now"). The upload panel keeps an optional "Schedule" field
  (default: immediate).
- Two additive endpoints manage an existing scheduled run:
  `PUT /api/v1/schedules/{id}` (`{schedule}` → updated run) and
  `POST /api/v1/schedules/{id}/run` (immediate trigger → created execution).
  Only `PENDING` runs can be rescheduled or triggered; a cron run is
  rescheduled after a manual trigger, a one-shot run becomes `COMPLETED`.
  New incoming port: `ManageScheduledRunUseCase`.

### Presentation

- Dark mode follows `prefers-color-scheme` and can be overridden with a
  persisted toggle (`data-theme` on `<html>`); all colours already come
  from CSS variables.
- Execution identifiers and schedule identifiers are click-to-copy; the
  executions list also offers a full-ID search box.
- Executions and scheduled runs use sortable, sticky table headers with
  elapsed/duration columns; cancelled runs get a distinct badge so they are
  not confused with failures.

## Consequences

- No public interface is modified; the schedule ports and endpoints are
  additive. `ScheduleRunService` gains `ScenarioParsingUseCase` and
  `SubmitScenarioUseCase` dependencies.
- Server-side filtering/pagination of executions is deliberately out of
  scope: `ExecutionRepository` is a public port and changing it requires an
  ADR. The list grows its `limit` with "Load more" instead.
- SSE remains a future option; polling is now visibility-aware and
  change-detecting.

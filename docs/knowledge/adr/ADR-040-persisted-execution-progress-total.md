# ADR-040 - Persist the planned execution task count

## Context

Counting only persisted results reports 100% after preparation, while injection
and assertions still have no results. An in-memory total would be lost on restart.

## Decision

- Add a non-negative `plannedTaskCount` to the framework-free `ExecutionState`.
- The engine initializes it from the submitted scenario before executing any step.
  All state updates preserve it; infrastructure persists it in a dedicated column.
- Progress uses the greater of this count and the number of distinct result task IDs.
  Existing `running` semantics include both running and pending tasks without results.
  Completed task and multi-agent success counting remain unchanged.
- Keep the previous constructor for existing callers, with count zero (unknown).
  The disposable development database is recreated using the initial schema.
  No incremental migration or backfill is provided.
- No protocol, framework dependency or database concept is added to application/domain.
  REST progress response fields remain unchanged.

## Consequences

New executions have a stable denominator across phases and application restarts.
Legacy executions must be rerun to obtain a reliable planned total.

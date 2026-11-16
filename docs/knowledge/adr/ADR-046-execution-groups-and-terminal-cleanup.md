# ADR-046 - Execution groups and terminal resource cleanup

**Status** : ACCEPTED  
**Date** : 2026-11-12

## Context

Concurrent example runs share a fixed embedded mock port and the SUT database.
Serializing only the mock START step would still allow competing preparations
to purge the same database. Failed assertions can skip the explicit STOP step
and leave the port bound indefinitely.

## Decision

Following user approval, `scenario.metadata.executionGroup` is an optional,
nonblank string identifying mutually exclusive campaign runs. The campaign
engine acquires the group before any preparation and releases it after terminal
events, including report generation and cleanup dispatch. Ungrouped runs and
different groups remain parallel. A queued cancellation never starts preparation.
This is process-local serialization, not a distributed lock across orchestrators.
The generic execution modules have no mock/port-specific scheduling policy.

The four examples use `example-sut` because they share infrastructure. Newly
uploaded scenarios must declare the same group when sharing those resources.

Bootstrap handles `ScenarioFinished` after the report listener. LOCAL invokes
execution-scoped `ExecutionCompletionCleaner` beans. This opt-in core interface
extends `StatefulResourceCleaner`; only the mock executor opts in initially.
DISTRIBUTED broadcasts the
existing `ScenarioRestartSignal` with reserved reason `EXECUTION_FINISHED` and
a mandatory execution ID. Agents invoke scoped cleaners only for this reason:
they do not cancel tasks, change task counters, or reset other executions' state.
Other restart reasons retain their previous semantics. The new opt-in interface
does not change existing cleaner method signatures. All participating agents
must be upgraded together.

Completion cleanup releases mock servers and their temporary mappings; reports
are rendered first. Ordinary restart cleaners are not called at completion,
preserving filesystem outputs and other existing resource lifecycle policies.
Failures are logged explicitly. Distributed cleanup is asynchronous and is not
an acknowledgement that every agent successfully released its resources.

The mock executor also waits, bounded by the START step timeout, for another
execution's owned port to be released. It never evicts that server. This covers
the gap between cleanup dispatch and delivery to the agent. Unmanaged occupied
ports remain explicit startup failures. Different ports can stay bound at once.
Failed starts stop their partial WireMock instance and release temporary mappings.

With explicit user approval, new Kafka command consumer groups start at `latest`
for tasks and signals, not `earliest`. Event consumers keep their existing offset
policy. Command subscription creation waits (maximum 30 seconds) for partition
assignment and initial offset resolution. The agent subscribes before publishing
its registration/heartbeats, preventing the first live dispatch from being skipped.
Signal consumers use the agent group name with a `-signals` suffix so starting
the second command subscription cannot rebalance the already-ready task consumer.
Old completed tasks must not be replayed as a restart recovery mechanism.
Committed offsets of an existing group are still honored by Kafka.

## Operations

Rebuild/recreate orchestrator and agents once, then upload the updated scenarios.
This also clears mocks leaked by older images. Changing the YAML on disk does not
modify a scenario previously stored in the catalog.
Do not delete Kafka topics or database volumes as a recovery measure.

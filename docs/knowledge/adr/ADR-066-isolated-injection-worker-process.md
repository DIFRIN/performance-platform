# ADR-066 - One isolated worker process per injection run

**Status** : PROPOSED (2026-11-16)
**Related** : ADR-007 (plugin JARs), ADR-046 (terminal cleanup), ADR-059 (LOCAL mode), ADR-065, ADR-068

## Context

Gatling runs inside the agent JVM (`DefaultGatlingRunner`):

- **No isolation.** The runner sets JVM-wide system properties (including the
  simulation id) and restores them afterwards, with no lock anywhere in the
  module. Two injections running at once on one agent overwrite each other's
  properties. `SimulationInjectionHolder` is static for the same reason.
- **No cancellation.** `GatlingTaskExecutor.cleanup()` only clears a map; its
  own Javadoc says cleanup is best-effort. A cancelled or timed-out execution
  keeps generating load until the simulation ends by itself.
- **Shared resources.** Gatling's heap, GC pauses and threads are those of the
  agent runtime, so a heavy run can delay heartbeats and signals, and the
  generator's own health cannot be measured separately.

## Decision

### 1. The agent forks one JVM per injection step

`GatlingTaskExecutor` no longer calls Gatling in-process. It starts a child
JVM (`InjectionWorkerMain`, in `platform-injection-gatling`) and waits for it
on a virtual thread.

- **Input**: a run directory containing the resolved plan (ADR-064), the grant
  share (ADR-067), the protocol specification and the resolved named
  resources. System properties of the step become `-D` arguments of the child
  only. Secrets are passed through the child environment, never written to the
  run directory.
- **Classpath**: the platform artefact plus the plugin directory (ADR-007), so
  custom simulations and protocol plugins load as they do today.
- **Output**: the Gatling results directory and one result file (ADR-068). The
  parent reads them after exit. Exit code `0` = completed, non-zero = failed,
  with the tail of the worker log attached to the `TaskResult`.
- **JVM sizing**: `injection.worker.jvm-args` (`INJECTION_WORKER_JVM_ARGS`).

### 2. Cancellation kills the process

On cancellation, step timeout or `cleanup(executionId)`:

1. signal the worker to stop new arrivals (`Process.destroy()`);
2. wait a bounded drain (`injection.worker.stop-grace`, default 10 s);
3. `destroyForcibly()`.

The result is marked incomplete and the number of in-flight requests at stop
is recorded when available. `StatefulResourceCleaner.cleanup` now really stops
the load. An agent shutdown kills its workers.

### 3. Concurrency is explicit

`injection.worker.max-concurrent` (default `1`). An agent at its limit does
not claim a new injection step. With `fanOut: one`, another agent claims it;
with no free agent, the claim window closes and the step fails explicitly.

**Interim rule**, applicable before the worker exists: an agent runs at most
one injection at a time, enforced by the same claim refusal. This removes the
system-property corruption immediately.

### 4. LOCAL mode uses the same path

LOCAL is the distributed engine with an embedded agent (ADR-059); the embedded
agent forks the same worker. There is no in-process fallback.

### 5. Generator health comes from the worker

The worker reports its own CPU time, GC pause time and peak heap in the result
file. They feed the generator-health metrics of ADR-068.

## Consequences

- Concurrent runs no longer interfere; static Gatling state is per process.
- Cancellation and timeouts stop the load within the grace period.
- The agent runtime stays responsive during heavy runs.
- Cost: JVM start-up (a few seconds, before the measured window), more memory
  per run, and a process to supervise. The container image already ships a
  JRE; memory limits must account for agent + worker.
- No public interface changes: `TaskExecutor` and `StatefulResourceCleaner`
  keep their signatures.
- `DefaultGatlingRunner` and `SimulationInjectionHolder` are reduced to the
  worker side.

## Rejected alternatives

- **A JVM-wide lock around in-process runs.** Fixes property corruption but
  not cancellation, not resource isolation, and serialises all runs.
- **A long-lived worker pool.** Faster start, but static Gatling state leaks
  between runs. Can be revisited with measurements.
- **One container per run.** Isolation is stronger, but it requires a container
  runtime on every agent and changes the deployment model (Spec 09).

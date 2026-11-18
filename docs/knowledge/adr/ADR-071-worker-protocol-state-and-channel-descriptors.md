# ADR-071 - Protocol state and channel descriptors for the injection worker

**Status** : ACCEPTED (2026-11-18)
**Related** : ADR-057 (executors as capability modules), ADR-066 (isolated worker), ADR-070

## Context

- The worker is a separate JVM (ADR-066). The generated simulations read their
  specification from static holders filled in the agent JVM, which the worker
  never sees. HTTP and Kafka generated simulations failed in the worker.
- HTTP state is plain data. The Kafka state holds a **live producer** opened by
  `MessageChannelFactory` from the agent's `platform.kafka-clusters`; it cannot
  cross a process boundary.
- `platform-injection-gatling` must not depend on a capability module such as
  `platform-executor-kafka` (ADR-057), and the worker has no Spring context.

## Decision

1. `PreparedInjection.workerState()` returns a JSON-safe snapshot written to
   `worker-state.json` in the run directory; the worker restores it before
   Gatling starts (`WorkerStateRestorer`, selected by the `type` key).
2. `plugin-api` (`plugin.resource`) gains a serialisable `ChannelDescriptor`
   (`type`, string `settings`), a `ChannelOpener` SPI discovered with
   `ServiceLoader` (`type()`, `open(descriptor, timeout)`), and a default method
   `MessageChannelFactory.describe(target, destination)`.
3. A capability module that owns a channel technology implements `describe`
   (resolved physical destination and connection settings) and a `ChannelOpener`
   registered in `META-INF/services`. The worker opens its **own** channel from
   the descriptor; no live object is transferred.
4. Descriptors carry **no secrets**: the run directory is written without
   secrets (ADR-066). A technology needing credentials must pass them through
   the worker environment, not through `settings`.
5. Kafka: `describe` yields `bootstrapServers`, `acks`, physical `topic`;
   `KafkaChannelOpener` rebuilds the producer with the same configuration and
   timeouts as `KafkaClusterRegistry`.

## Consequences

- Generated HTTP and Kafka simulations run unchanged in the worker.
- `plugin-api` gains three small types; existing `MessageChannelFactory`
  implementations keep compiling (default method).
- Adding a messaging technology needs a `describe` and an opener, nothing in
  the gatling module.

## Rejected alternatives

- **Re-run `protocol.prepare` in the worker.** Needs the agent's Spring beans
  and configuration there.
- **Worker depends on the Kafka executor module.** Breaks ADR-057.
- **Write the full cluster properties (with credentials) to the run directory.**
  Secrets on disk.

# ADR-056 - Reduced transport surface

**Status** : ACCEPTED (2026-11-15)

## Context

`platform-transport` ships five implementations (in-memory, Kafka, RabbitMQ,
HTTP, socket) plus a CUSTOM extension point. Each one must honour the same
delivery guarantees, TLS options (ADR-047), codecs, lifecycle and loss
detection, and each new protocol message (for example the grant of ADR-055)
must be implemented and tested five times. No current example or deployment
uses RabbitMQ or the raw socket transport.

## Decision

- Supported transports are:
  - **in-memory** — LOCAL mode and tests;
  - **HTTP** — the default for DISTRIBUTED, no broker required;
  - **Kafka** — for deployments that already operate a broker.
- RabbitMQ and socket are removed from the core build. They may live as
  external transport JARs through the existing CUSTOM extension point.
- The transport ports (`ExecutionTransport` and the segregated interfaces of
  ADR-028) are unchanged. Selection stays `@ConditionalOnProperty` only.
- A transport contract test suite (one abstract test class, run against each
  supported implementation) is the definition of "supported".

## Consequences

Two packages, their dependencies and their tests leave the build. Protocol
evolutions cost three implementations instead of five and are verified by one
shared suite. Users of RabbitMQ or socket must supply the transport as a
plugin; the CUSTOM path becomes exercised and documented rather than
theoretical. Spec 05 section 4 and the deployment examples must be updated.

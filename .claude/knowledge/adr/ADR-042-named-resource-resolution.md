# ADR-042 - Neutral named resource resolution

## Context

Preparation resolves named datasources from the infrastructure registry, while
database assertions looked up only Spring bean names. A configured `sut-db` entry
therefore worked in preparation but failed in assertions.

## Decision

Expose `NamedResourceResolver<T>` in application: logical name to resource, or null
if unknown. The contract contains no protocol, framework or storage technology.
Infrastructure `DatasourceProvider` implements it for its configured resources.
Assertions request the appropriately typed resolver without depending on infrastructure.

Configured logical resources take precedence over named Spring beans. Existing
named bean lookup and the primary-only `default` alias remain compatible. Unknown
names never fall back to an unrelated primary resource. Bean initialization or
type errors must propagate rather than being hidden as a missing resource.

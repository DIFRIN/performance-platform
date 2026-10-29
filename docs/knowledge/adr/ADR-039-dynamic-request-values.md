# ADR-039 — Dynamic request values and generic Gatling HTTP injection

**Status** : ACCEPTED

## Context
Scenarios need per-request variable content (incremental / computed / random ids) in the path, query
params, headers and body of `http-client` steps and of HTTP load injection. Gatling injection
previously required a user-provided simulation class (`simulation:`), which must not be part of
the platform code.

## Decision
1. `platform-plugin-api` exposes `com.performance.platform.domain.template.DynamicValues` (additive,
   no framework dependency): placeholders `{index[±*/%N][:width]}`, `{uuid}`, `{timestamp}`, `{date[:pattern]}`,
   `{random[:min-max]}`, `{randomString:n}`, `{randomHex:n}`, `{choice:a|b}`, `{cycle:a|b}`,
   `{executionId}`, `{stepId}`. Unknown `{names}` are left untouched; invalid arguments throw
   `IllegalArgumentException`. `toRegex` yields a matching regex (used by the `http-mock` assertion).
2. `http-client` accepts `pathParams`, `queryParams`, `headers`, `body` (String or map/list → JSON).
3. `gatling` without `simulation` is an HTTP injection built from the step parameters:
   `HttpRequestSpecFactory` → `HttpRequestSpecHolder` → built-in `HttpScenarioSimulation`,
   which renders each request with a global 1-based `{index}`. The target comes from the
   `EndpointResolver` port (ADR-036) or an explicit `baseUrl`. `simulation:` stays optional for
   custom simulations.

## Consequences
- No example/test simulation class is declared in the code base.
- `platform-injection-gatling` depends on `platform-use-cases` (port) and `jackson-databind` (body).
- `{index}` is 1 in `http-client` (single request).

## Addendum — SPI de protocole et injection Kafka native

- GatlingTaskExecutor (sans simulation) délègue à un bean InjectionProtocol choisi par protocol (http défaut, kafka) ; ajouter un protocole = ajouter un bean.
- Kafka : action Gatling native (KafkaPublishAction), sans nouvelle dépendance Maven. Publication via les ports MessageChannel/MessageChannelFactory (platform-use-cases), implémentés par KafkaClusterPublisherFactory (infra) : gatling ne dépend jamais d'infra.
- MessageTemplate (plugin-api) factorise key/headers/payload dynamiques pour kafka-producer (préparation) et l'injection.
- kafka-producer perd duration/atePerSecond : cadence et durée relèvent du loadModel du step gatling.


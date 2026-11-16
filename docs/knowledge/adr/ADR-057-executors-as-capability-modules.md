# ADR-057 - Executors as capability modules

**Status** : ACCEPTED (2026-11-16)

## Context

`infra/platform-infrastructure` contains orchestrator persistence (JPA,
Flyway), scheduling, plugin loading and every built-in executor (database,
docker, filesystem, http, kafka, mock, shell). Its dependencies include
Hibernate, Flyway, spring-kafka and WireMock together.

Agents are specialised by configuration (ADR-015) and expose no server
(ADR-019), yet every agent carries all of these libraries. Built-in executors
are wired differently from external ones, so the plugin-JAR system (ADR-007) is
not proven by the platform's own executors.

## Decision

- `platform-infrastructure` is split:
  - `platform-persistence` — JPA, Flyway, repositories, scheduling store.
    Orchestrator and LOCAL only.
  - one module per executor family: `platform-executor-database`,
    `-http`, `-kafka`, `-mock`, `-shell`, `-filesystem`, `-docker`.
- An executor module depends on `platform-plugin-api` and `platform-domain`
  only, plus its own technical libraries. It never depends on persistence, on
  another executor module, or on `execution/` (ADR-045 is kept).
- Built-in executors are discovered exactly like external plugin executors
  (Spring bean auto-discovery, `@Preparation` / `@Injection` / `@Assertion`).
  There is no built-in-only wiring path.
- The single artifact (ADR-005) still bundles all built-in modules. The split is
  a build and dependency boundary; trimmed agent images become possible but are
  not required by this ADR.
- The boundary is enforced by the existing bytecode dependency test (ADR-035).

## Consequences

A change to one executor rebuilds and tests one small module. The plugin API is
validated by seven real consumers; anything a built-in executor needs and
cannot get through `platform-plugin-api` is an API gap to fix there, not a
shortcut to take. Named resources shared by several executors (datasources,
HTTP targets, clusters: ADR-042) must be reachable through a port, not through
a shared infrastructure class. The Maven reactor gains about seven modules.
CLAUDE.md section 6 and `architecture.md` section 3 must be updated.

## Note d'implémentation (2026-11-16)

Livré :
- `infra/platform-persistence` : paquet `infrastructure.persistence`, migrations
  Flyway, et `PersistenceDataSourceConfiguration` (DataSource primaire
  `platform.datasources.default`, auparavant créée par `DatasourceConfiguration`
  du module database). `JpaConfiguration` et les repositories JPA sont activés par
  la propriété `platform.datasources.default.url` au lieu de
  `@ConditionalOnBean(DataSource.class)`, qui dépendait de l'ordre de scan.
- `infra/executors/platform-executor-{database,docker,filesystem,http,kafka,mock,shell}` :
  un module par famille, noms de paquets inchangés (`infrastructure.executor.<famille>`),
  donc aucun import à migrer hors du déplacement.
- `platform-infrastructure` ne garde que le socle commun : `TaskExecutorRegistry`,
  chargement des plugins, `SpringScheduleCalculator`. Plus de JPA, Kafka, WireMock
  ni Hikari dans ses dépendances.
- Le prérequis noté le 2026-11-15 était plus étroit qu'annoncé : `HttpTargetRegistry`,
  `DatasourceProvider` et `KafkaClusterRegistry` n'étaient utilisés que par leur
  propre famille, et implémentaient déjà les ports `EndpointResolver` /
  `NamedResourceResolver`. Le seul couplage inter-familles était `mock-server` →
  `HttpTargetRegistry` ; il passe désormais par `ObjectProvider<EndpointResolver>`
  (le module mock fonctionne sans le module http ; une cible externe sans
  résolveur est une erreur explicite).
- Frontières vérifiées par `ModuleDependencyTest` (ASM) : executor ↛ autre executor,
  executor ↛ persistence, persistence ↛ executor, `jakarta.persistence` hors de
  `platform-persistence`. Les tests ArchUnit `InfrastructurePackageSeparationTest`
  et `PersistenceConfinementTest` sont supprimés : ils étaient vides sur du
  bytecode Java 25 (voir `architecture.md` §3) et la séparation est maintenant
  imposée par Maven.

Contrats déplacés dans `platform-plugin-api` (2026-11-16) pour respecter la
décision à la lettre (un executor ne dépend que de `platform-plugin-api` et
`platform-domain`) :
- `plugin.resource` : `Endpoint`, `EndpointResolver` (ADR-036),
  `NamedResourceResolver` (ADR-042), `MessageChannel`, `MessageChannelFactory` —
  auparavant `application.ports.out` ;
- `plugin.template` : `DynamicValues`, `MessageTemplate`, `JsonText` (ADR-039) —
  auparavant `application.template`.
Ces types sont sans dépendance hors JDK ; `platform-plugin-api` reste sans
framework. Aucun autre type de `platform-use-cases` ne les utilisait. Les quatre
modules d'executor concernés n'ont plus `platform-use-cases` dans leur pom, et
`ModuleDependencyTest` interdit executor → `com.performance.platform.application`.
Les plugins externes qui utilisaient ces types doivent être recompilés avec les
nouveaux paquets. `platform-injection-gatling` et `platform-assertion-adapters`
importent aussi les nouveaux paquets ; leur dépendance devenue inutile à
`platform-use-cases` est retirée.

# ADR-025 — Assemblage runtime de platform-app + beans d'infrastructure conditionnels

**Date** : 2026-06-29
**Statut** : ACCEPTED
**Décideurs** : Architect (décision utilisateur)
**Lié à** : ADR-013 (Spring-first infra), ADR-014 (datasource configuration), ADR-019
(AGENT = `WebApplicationType.NONE`), ADR-021 (CLI headless), CF-01 (artefact unique),
CF-03 (`@ConditionalOnProperty` uniquement, pas `@Profile`).

---

## Contexte

Le même fat JAR doit démarrer en **LOCAL**, **ORCHESTRATOR** ou **AGENT** avec un simple
`application.yaml` (CF-01). En auditant le code réel, deux problèmes distincts mais liés
empêchent ce démarrage :

### Problème A — `platform-app` n'a aucun bean d'assemblage du cœur d'exécution

Les contrôleurs REST (`ScenarioController`, …) et l'adapter CLI (`CliScenarioRunner`)
dépendent de use cases (ports in) **qui n'ont aucune implémentation déclarée comme bean dans
`src/main`**. Ils n'existent que **construits à la main dans les tests E2E**
(`LocalFlowE2ETest.@BeforeAll`, `WebUiApiE2ETest`, `LocalModeAllTasksE2ETest`).

Beans manquants en `src/main` (présents uniquement dans les tests) :

| Bean manquant | Requis par | Constat |
|---|---|---|
| `TaskExecutorLookup` | `LocalExecutionEngine` (`@Service`, `runtime.mode=LOCAL`) | Javadoc de l'interface : « sera bridgée vers `TaskExecutorRegistry` lorsque PDR-010 sera implémenté » → jamais fait. Stub uniquement en test. |
| `ScenarioParsingUseCase` (`DefaultScenarioParsingService`) + `YamlScenarioParser` + `DefaultScenarioValidator` | `ScenarioController`, `CliScenarioRunner` | Aucune annotation, aucun `@Bean`. |
| `ExecuteScenarioUseCase` | `ScenarioController`, `CliScenarioRunner` | Lambda `engine.execute()` en test. |
| `GetExecutionStatusUseCase` | `ScenarioController`, `CliScenarioRunner` | Classe anonyme en test. |
| `CancelExecutionUseCase` | `ScenarioController` | No-op en test. |
| `ExecutionConfig` | `RemoteExecutionEngine` (`@Service`, `runtime.mode=DISTRIBUTED`) | Record `new`'d uniquement en test. |
| `AgentRegistry` / `AgentRegistryPort` (`InMemoryAgentRegistry`) | `DefaultAgentAvailabilityChecker` → `RemoteExecutionEngine` | Javadoc : « câblage Spring sera ajouté… ISSUE-077/PDR-018 » → jamais fait. |

Conséquence : un démarrage serveur (LOCAL/ORCHESTRATOR) **échoue** car les contrôleurs ne
peuvent pas se câbler ; le `LocalExecutionEngine` lui-même ne s'instancie pas (pas de
`TaskExecutorLookup`). Les `@Service`/`@Component` existants (engines déjà
`@ConditionalOnProperty(runtime.mode=…)`, plan builder, retry, mappers, `JpaExecutionRepository`,
`DefaultReportEngine` + son `@EventListener` de génération de rapport) sont corrects ; ce qui
manque, c'est la **colle d'assemblage** entre ports in et ces composants.

### Problème B — Les beans d'infrastructure DB ne sont pas conditionnels

- `DatasourceConfiguration.dataSource()` est **inconditionnel et lève `IllegalStateException`**
  s'il n'y a pas de `platform.datasources.default` → casse le mode **AGENT** (`datasources: {}`).
- `JpaConfiguration` (EMF + txManager + `@EnableJpaRepositories`) et `JpaExecutionRepository`
  (`@Repository`) sont **inconditionnels** → JPA tente de s'initialiser sans `DataSource` en AGENT.
- `KafkaClusterConfiguration` / `HttpTargetConfiguration` créent toujours leur registry.

La **couche transport** est déjà exemplaire (`TransportConfiguration` / `KafkaTransportBeans`
en `@ConditionalOnProperty(transport.type=…)`, IN_MEMORY par défaut) — **modèle de référence**.

---

## Décision

**1. Créer dans `platform-app/src/main/.../config/` les `@Configuration` d'assemblage runtime**
qui exposent, comme beans Spring, l'implémentation des use cases manquants et leurs
dépendances, de sorte que l'application s'assemble depuis un simple `application.yaml` — **sans
aucun bean de test**.

- `TaskExecutorLookup` : un bean-bridge délègue à `TaskExecutorRegistry` (infra) et
  `AssertionExecutorRegistry` (assertion). `findTaskExecutor` capture
  `UnsupportedTaskNameException` → `null` ; `findAssertionExecutor` idem.
- `ScenarioParsingUseCase` : `new DefaultScenarioParsingService(parser, validator)` avec
  `YamlScenarioParser` et `DefaultScenarioValidator` exposés en beans.
- `ExecuteScenarioUseCase` → délègue à `ExecutionEngine.execute(scenario)` (l'unique
  `ExecutionEngine` actif selon `runtime.mode`).
- `GetExecutionStatusUseCase` → `ExecutionEngine.getStatus(id)` + `ExecutionRepository.findById`
  pour `getState`.
- `CancelExecutionUseCase` → `ExecutionEngine.cancel(id)`.
- `ExecutionConfig` : bean construit depuis des défauts/properties (requis par
  `RemoteExecutionEngine`).
- `AgentRegistry`/`AgentRegistryPort` (`InMemoryAgentRegistry`) : bean **conditionnel
  ORCHESTRATOR** (`runtime.mode=DISTRIBUTED` + `runtime.role=ORCHESTRATOR`).

Les use cases mode-agnostiques (Execute/Status/Cancel/Parsing/Lookup) injectent les beans
actifs et fonctionnent quel que soit le mode ; les beans propres à DISTRIBUTED
(`ExecutionConfig` consommé par RemoteExecutionEngine déjà conditionnel, `AgentRegistry`) sont
gardés par condition.

**2. Rendre conditionnels les beans d'infrastructure DB** (`platform-infrastructure`) :

- `DatasourceConfiguration.dataSource()` → `@ConditionalOnProperty(prefix =
  "platform.datasources.default", name = "url")` ; **supprimer le `throw`** (absence ⇒ pas de
  bean, log info).
- `JpaConfiguration` → `@ConditionalOnBean(DataSource.class)`.
- `JpaExecutionRepository` → conditionnel à la présence de JPA/`DataSource`
  (`@ConditionalOnBean(DataSource.class)` ou sur le repository Spring Data), pour qu'AGENT
  démarre sans `ExecutionRepository`.
- Registries Kafka/HTTP : alignés sur le modèle conditionnel (`matchIfMissing = true` admis
  quand un registry vide est un défaut sûr).

**3. `@ConditionalOnProperty` / `@ConditionalOnBean` / `@ConditionalOnClass` uniquement** —
jamais `@Profile` (CF-03). Le mode runtime pilote les properties (`runtime.mode`,
`runtime.role`, `transport.type`, `platform.datasources.*`), qui pilotent les conditions.

**4. Tests = smoke tests de démarrage par mode** (`SpringApplicationBuilder`, **pas
Testcontainers**, pas `@SpringBootTest` — incompatible Spring Boot 4.0.0 + JUnit 5.11.4 dans ce
repo) : démarrer le contexte avec les properties de chaque mode et asserter la présence/absence
des beans attendus. AGENT en `WebApplicationType.NONE`. Aucun bean de test ne doit suppléer le
câblage de production.

---

## Justification

- **CF-01** : l'artefact unique démarre réellement dans les trois modes à partir d'un
  `application.yaml`, sans dépendre de beans définis dans les tests.
- **Honnêteté des tests** : les E2E cessent de reconstruire à la main la moitié de
  l'application ; ils exercent le **vrai** assemblage de production.
- **Robustesse / AGENT** : l'absence de DB ne fait plus planter le contexte ; les beans DB
  n'existent que si configurés.
- **Cohérence** : on réplique le modèle conditionnel déjà validé du transport.

---

## Conséquences

**Positives** :
- L'app démarre en LOCAL/ORCHESTRATOR/AGENT avec la seule config YAML.
- Suppression de la duplication src/test ; E2E simplifiés à terme.
- AGENT plus léger (pas de DataSource/JPA — CNF-05).

**Négatives / Contraintes** :
- Création de plusieurs `@Configuration`/beans dans `platform-app` (colle d'assemblage).
- Les E2E existants qui câblaient tout à la main pourront être simplifiés (hors périmètre
  immédiat ; ne pas casser ceux qui passent).
- Vérifier que les consommateurs de `DataSource`/`ExecutionRepository` tolèrent leur absence
  en AGENT.

**Fichiers impactés** :
- `platform-app/.../config/` — nouvelles `@Configuration` d'assemblage (use cases, lookup,
  parsing, ExecutionConfig, AgentRegistry).
- `platform-infrastructure/.../executor/database/DatasourceConfiguration.java`,
  `.../persistence/config/JpaConfiguration.java`, `.../persistence/JpaExecutionRepository.java`,
  `.../executor/kafka/KafkaClusterConfiguration.java`, `.../executor/http/HttpTargetConfiguration.java`
  — conditions.
- `platform-app/src/test/...` — smoke tests de démarrage par mode.

---

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Laisser le câblage dans les tests E2E | L'app ne démarre pas en production ; viole CF-01. |
| Annoter les use cases/parsers directement (`@Service`) dans leurs modules respectifs | `platform-application` est volontairement framework-agnostique ; le câblage doit vivre dans `platform-app` (assemblage), conformément au pattern existant (`ExecutionUseCaseConfiguration`). |
| `@Profile("local"/"orchestrator"/"agent")` | Interdit (CF-03) ; multiplie les artefacts logiques. |
| Garder le `throw` datasource | Casse AGENT ; viole CF-01. |
| Smoke tests via Testcontainers/`@SpringBootTest` | Inutilement lourd ; `@SpringBootTest` incompatible Spring Boot 4.0.0 + JUnit 5.11.4 dans ce repo. `SpringApplicationBuilder` suffit. |
</content>

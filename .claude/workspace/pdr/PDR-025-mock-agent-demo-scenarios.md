# PDR-025 — Mock Agent Demo Scenarios + Device Code Cleanup

**Module Maven** : `platform-deployment` (scenarios, docker-compose), `platform-infrastructure` (cleanup)
**Package** : `com.performance.platform.infrastructure.executor.mock` (MockServerTaskExecutor — existant)
**Statut** : IN PROGRESS
**Specs de reference** : `.claude/knowledge/specs/01-scenario-dsl.md`, `.claude/knowledge/specs/04-agent-runtime.md`, `.claude/knowledge/specs/07-assertion-framework.md`, `.claude/knowledge/adr/ADR-015-configuration-driven-agent-specialization.md`
**Depend de** : PDR-010 (Task Executors), PDR-013 (Gatling), PDR-014 (Assertion), PDR-018 (App Assembly), PDR-019 (Docker compose), PDR-022 (HttpTargetRegistry), PDR-023 (SUT Examples), PDR-024 (IoT Scenarios) — TOUS DONE
**Issues** : ISSUE-103, ISSUE-104, ISSUE-105, ISSUE-106, ISSUE-107, ISSUE-108, ISSUE-109, ISSUE-110

---

## Responsabilite

Ce PDR effectue un **cleanup architectural** et livre des scenarios de demonstration "WireMock as Agent" :

1. **Supprime le code device example-only du module production** `platform-infrastructure`. Les `DevicePopulationTaskExecutor` et `DeviceCheckTaskExecutor` sont du code d'exemple incorrectement place dans le module de production. Ils sont supprimes. La simulation de devices vit exclusivement dans `platform-examples/` ou elle appartient en tant que code SUT de demo.
2. **Supprime le service WireMock standalone** de `docker-compose-sut.yaml`. WireMock ne doit pas etre un service Docker separe — il s'execute EMBEDDED dans un agent via `MockServerTaskExecutor` en phase PREPARATION.
3. **Cree les scenarios demo "Mock as Agent"** (LOCAL et DISTRIBUTED) qui demontrent le workflow complet : preparation (mock-server EMBEDDED) -> injection (Gatling HTTP) -> assertion (http-mock) -> rapport.
4. **Supprime le scenario legacy** `device-check-perf.yaml` qui dependait des `DevicePopulationTaskExecutor`/`DeviceCheckTaskExecutor` supprimes.
5. **Cree le docker-compose specifique** pour les agents specialises (mock-agent, gatling-agent, standard-agent).
6. **Documente l'architecture Mock-as-Agent** dans le README.

**WireMock agent model** :
- Un agent demarre WireMock **in-process** en phase PREPARATION (port auto-assign ou configurable)
- Les autres steps (injection, assertion) referencent ce mock via son URL publiee dans `TaskResult.outputs`
- **Pas de service WireMock standalone** dans docker-compose — les mocks sont des agents

**Ce que ce PDR NE fait PAS** :
- Ne cree pas de nouveau TaskExecutor (MockServerTaskExecutor existe deja dans platform-infrastructure)
- Ne modifie pas l'API publique (`TaskExecutor`, `ExecutionContext`, `Transport`)
- Ne touche pas a `platform-domain` ou `platform-plugin-api`

---

## Modele Configuration-Driven pour la Specialisation des Agents

### Principe fondamental

La specialisation des agents est **entierement configuration-driven**. Aucune auto-discovery des `supportedTaskNames` a partir des annotations `@Preparation`/`@Injection`/`@Assertion`. Les annotations sont conservees UNIQUEMENT pour `PluginLoader` (resolution task-name -> implementation). Les `supportedTaskNames` proviennent EXCLUSIVEMENT de la configuration `agent.supported-tasks` dans `application.yml`.

```
Configuration (application.yml)     Runtime (AgentDescriptor)
────────────────────────────────    ──────────────────────────
agent:                              AgentDescriptor {
  supported-tasks:                      supportedTaskNames: ["mock-server", "http-client"]
    - mock-server                  }
    - http-client

ETAPE 1: PluginLoader scanne les annotations pour trouver l'implementation du task-name
ETAPE 2: AgentDescriptor.supportedTaskNames vient de la config — PAS des annotations
ETAPE 3: TaskSpecializationFilter filtre les TaskExecutionRequest selon supportedTaskNames
```

### Comportement par mode

**LOCAL mode** :
- Une seule instance JVM = orchestrateur + agent + tous les TaskExecutors
- `LocalAgent` declare TOUS les `supportedTaskNames` (tous les noms du `TaskExecutorRegistry`) — pas de filtrage
- `LocalAgent.canExecute()` retourne toujours `true`
- La specialisation est non-pertinente en LOCAL : tout est disponible par definition
- Les scenarios LOCAL n'ont PAS de champ `agentTags` — le routing n'a pas de sens en mode LOCAL
- Tout step est execute in-process, sans dispatch

**DISTRIBUTED mode** :
- Chaque agent declare explicitement `agent.supported-tasks` dans sa config
- L'orchestrateur dispatche en broadcast (pas de selection ciblee)
- Chaque agent filtre localement via `TaskSpecializationFilter` base sur `supportedTaskNames`
- Le routing est par `taskName` uniquement — JAMAIS par tags
- `agentTags` est COMPLETEMENT SUPPRIME des scenarios — le champ n'existe plus
- Plusieurs agents peuvent avoir la meme task dans `supported-tasks` (multi-claim, completionPolicy)

### Roles des annotations `@Preparation`/`@Injection`/`@Assertion`

| Usage | Conserve ? | Detail |
|---|---|---|
| PluginLoader: resolution task-name -> implementation | OUI | `AnnotationScanner` trouve l'executor par son `name` |
| Derivation automatique de `supportedTaskNames` | NON | Les `supportedTaskNames` viennent de `agent.supported-tasks` |
| Enregistrement dans `TaskExecutorRegistry` | OUI | Le `name` de l'annotation = cle dans le registre |
| Validation CF-07 (annotation obligatoire) | OUI | Tout TaskExecutor doit avoir exactement une annotation |

### Cycle de vie d'un plugin/task non reference

- Si un plugin JAR contient un TaskExecutor avec `@Preparation(name = "mon-plugin")` mais que `agent.supported-tasks` ne contient PAS `mon-plugin` : le PluginLoader charge l'executor, le TaskExecutorRegistry l'enregistre, mais l'agent ne l'execute JAMAIS (filtre `TaskSpecializationFilter` le rejette)
- Si un plugin est charge mais qu'aucun scenario ne reference son task-name : idle permanent
- **Tout est driven par la configuration**, pas par ce qui est sur le classpath

### Note : Implementation du mecanisme `agent.supported-tasks`

Le mecanisme complet (configuration `agent.supported-tasks` -> `AgentDescriptor.supportedTaskNames` -> `TaskSpecializationFilter` -> filtrage broadcast) est specifie dans **PDR-009** (Agent Runtime) et assemble dans **PDR-018** (App Assembly). Ce PDR (PDR-025) **utilise** ce mecanisme dans ses scenarios et docker-compose — il ne l'implemente pas.

**MISE A JOUR REQUISE** : PDR-009 et PDR-018 doivent etre verifies pour s'assurer que `supportedTaskNames` est derive de la configuration `agent.supported-tasks` et NON des annotations. Si ce n'est pas le cas, des Issues de correction doivent etre creees. Voir la note dans `.claude/workspace/progress.md`.

---

## Architecture Cible : WireMock as Agent

```
Scenario: http-api-mock-agent

ETAPE 1 (PREPARATION) : spawn-mock
  ├── task: mock-server
  ├── deployment: EMBEDDED
  ├── port: 0 (auto-assign)
  └── Agent "mock-agent" (agent.supported-tasks: [mock-server, http-client]) recoit, lance WireMock in-process
      └── Outputs: { port: 35421, url: "http://agent-1:35421" }

ETAPE 2 (INJECTION) : load-test
  ├── task: gatling
  ├── target: mock-api          ── resolution via HttpTargetRegistry
  ├── dependsOn: [spawn-mock]
  └── Agent "gatling-agent" (agent.supported-tasks: [gatling, gatling-metric]) recoit, lance Gatling contre le mock

ETAPE 3 (ASSERTION) : assert-mock-hit
  ├── task: http-mock
  ├── target: mock-api          ── resolution vers l'URL du mock-agent
  ├── dependsOn: [load-test]
  └── Agent "standard-agent" (agent.supported-tasks: [http-mock, database, kafka]) execute la verification WireMock a distance (mode EXTERNAL)
```

**En LOCAL** : Une seule instance. `LocalAgent` possede tous les `supportedTaskNames` (tous les noms du registre). Tout est in-process. Aucun dispatch, aucun filtrage, aucun `agentTags` dans le scenario.

**En DISTRIBUTED** : 3 agents specialises :
- `mock-agent` (agent.supported-tasks: [mock-server, http-client])
- `gatling-agent` (agent.supported-tasks: [gatling, gatling-metric])
- `standard-agent` (agent.supported-tasks: [http-mock, database, kafka])

Le routing est par `task:` name UNIQUEMENT. L'orchestrateur broadcast, les agents filtrent.

---

## Interfaces Publiques

> Aucune nouvelle interface. Ce PDR est purement scenario + documentation + cleanup + docker-compose.

```java
// MockServerTaskExecutor existe deja dans platform-infrastructure.
// Il supporte le mode EMBEDDED (WireMock in-process) pour l'agent model.
// Voir PDR-010 et platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/mock/MockServerTaskExecutor.java

// Le MockServerTaskExecutor existant :
// @Preparation(name = "mock-server", version = "1.1.0")
// public class MockServerTaskExecutor implements TaskExecutor, StatefulResourceCleaner {
//     public String getSupportedTaskName() { return "mock-server"; }
//     public TaskResult execute(ExecutionContext context, StepDefinition step) { ... }
//     // EMBEDDED: WireMock in-process -> outputs { port, url }
//     // EXTERNAL: remote WireMock admin API -> outputs { port, url }
// }
```

---

## Regles de Comportement

- **Configuration-driven specialization** :
  - `agent.supported-tasks` dans `application.yml` (hyphenated YAML, mappe a `supportedTasks` en Java)
  - `AgentDescriptor.supportedTaskNames` = `Set<String>` derive de cette config
  - `TaskSpecializationFilter` utilise `supportedTaskNames` pour le filtrage broadcast
  - **Pas d'auto-discovery** des supportedTaskNames depuis les annotations

- **En LOCAL** :
  - `LocalAgent.supportedTaskNames` = tous les noms du `TaskExecutorRegistry`
  - `LocalAgent.canExecute(taskName)` retourne toujours `true`
  - Aucun champ `agentTags` dans les scenarios LOCAL
  - Aucun dispatch — tout est in-process

- **En DISTRIBUTED** :
  - Chaque agent a `agent.supported-tasks` dans sa config
  - `agentTags` COMPLETEMENT SUPPRIME des scenarios — ne pas utiliser
  - Routing par `task:` name UNIQUEMENT
  - Broadcast + filtrage cote agent

- **Annotations `@Preparation`/`@Injection`/`@Assertion`** :
  - Conservees pour PluginLoader (task-name -> implementation)
  - NE SERVENT PLUS a deriver `supportedTaskNames`
  - Leur `name` reste la cle de resolution dans le YAML (`task: mock-server`)

- **Plugin non reference** :
  - Plugin charge par PluginLoader mais task-name absent de `agent.supported-tasks` -> jamais active
  - Plugin charge mais task-name absent de tout scenario -> jamais execute (idle)

- **WireMock agent lifecycle** :
  - DEMARRAGE : `task: mock-server`, `action: START`, `deployment: EMBEDDED`, `port: 0` (auto) -> l'agent demarre WireMock in-process, stocke port+url dans outputs
  - DEMARRAGE (mappings) : `mappingsPath: classpath:mock/mappings` -> fichiers de stubs charges depuis le classpath
  - ARRET : `action: STOP` -> arrete le serveur WireMock pour cet executionId
  - RESET : `action: RESET` -> reinitialise les compteurs et mappings entre deux runs
  - VERIFY : `action: VERIFY` -> retourne `{ totalRequests: N }` dans outputs

- **`port: 0` (auto-assign)** : En mode DISTRIBUTED, l'agent assigne automatiquement un port disponible et publie l'URL reelle dans `TaskResult.outputs`. Le scenario n'a pas besoin de connaitre le port a l'avance.

- **Communication mock-agent <-> autre agent** : Les agents communiquent via le reseau Docker (ex: `http://mock-agent:35421`). L'URL est stockee dans `ExecutionContext` et resolue via `HttpTargetRegistry` au runtime.

- **Suppression du code device dans platform-infrastructure** : Les classes `DevicePopulationTaskExecutor` et `DeviceCheckTaskExecutor` sont supprimees. Ce sont des executors specifiques a une demo qui n'ont pas leur place dans le module de production. La simulation de devices existe dans `platform-examples/` (SUT iot-dispatcher, device-api).

- **Docker-compose SUT** : Supprimer le service `wiremock` standalone de `docker-compose-sut.yaml`. Les mocks sont maintenant geres par l'agent `mock-agent` en EMBEDDED. Garder uniquement `postgres-sut`, `kafka-sut`, `iot-dispatcher`, `device-api`.

- **Nouveau docker-compose WIREMOCK-AS-AGENT** : Un fichier `docker-compose-wiremock-agent.yaml` pour les demos avec : `postgres`, `kafka`, `orchestrator`, `mock-agent`, `gatling-agent`, `standard-agent`. Chaque agent recoit `AGENT_SUPPORTED_TASKS` comme variable d'environnement (mappe a `agent.supported-tasks`). Meme image Docker pour tous, config differente = role different.

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-010 (Task Executors)              -> MockServerTaskExecutor existant (EMBEDDED/EXTERNAL)
  PDR-013 (Gatling Injection)           -> GatlingTaskExecutor pour le load HTTP
  PDR-014 (Assertion Framework)         -> HttpMockAssertionExecutor pour assertions mock
  PDR-018 (App Assembly)                -> platform-app pour lancer LOCAL/DISTRIBUTED
  PDR-019 (Docker compose)              -> docker-compose.yaml modele pour wiremock-agent
  PDR-022 (HttpTargetRegistry)          -> target: mock pour resolution d'URL
  PDR-023 (SUT Examples)                -> references aux SUT existants
  PDR-024 (IoT Scenarios)              -> modele de scenario pour LOCAL/DISTRIBUTED

Ce PDR est utilise par :
  (futures demos avancees)             -> modele de reference pour mock-as-agent
```

---

## NOTES DE SUIVI — Mecanisme `agent.supported-tasks`

Le mecanisme complet de specialisation configuration-driven est implemente dans :
- **PDR-009** (Agent Runtime) : `AgentDescriptor.supportedTaskNames`, `TaskSpecializationFilter`, `LocalAgent`, `DistributedAgentRuntime`
- **PDR-018** (App Assembly) : configuration `agent.supported-tasks`, mapping env var -> config, assemblage Spring

**ACTION REQUISE (hors PDR-025)** : Verifier que PDR-009 et PDR-018 implementent correctement le modele configuration-driven avec `agent.supported-tasks` (pas d'auto-discovery depuis les annotations). Si le code actuel derive `supportedTaskNames` des annotations ou utilise `agentTags` pour le routing, creer des Issues de correction.

Suivi dans `progress.md` — ligne PDR-025.

---

## Criteres de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] `DevicePopulationTaskExecutor` et `DeviceCheckTaskExecutor` supprimes de `platform-infrastructure`
- [ ] `device-check-perf.yaml` supprime
- [ ] `docker-compose-sut.yaml` n'a PLUS de service `wiremock` standalone
- [ ] `docker-compose-wiremock-agent.yaml` cree avec mock-agent + gatling-agent + standard-agent
- [ ] Chaque agent dans docker-compose utilise `AGENT_SUPPORTED_TASKS` env var (pas `AGENT_TAGS`)
- [ ] Scenarios `http-api-mock-agent-local.yaml` et `http-api-mock-agent-distributed.yaml` crees et parseables
- [ ] Aucun champ `agentTags` dans les nouveaux scenarios
- [ ] `README.md` mis a jour avec l'architecture Mock-as-Agent + modele configuration-driven
- [ ] `interfaces-registry.md` nettoye des entrees device
- [ ] `mvn test -pl platform-infrastructure,platform-deployment -q` -> 0 erreur
- [ ] Aucune URL inline dans les nouveaux scenarios — uniquement `target:`, references logiques
- [ ] Note dans `progress.md` pour le suivi PDR-009/PDR-018 (mecanisme `agent.supported-tasks`)

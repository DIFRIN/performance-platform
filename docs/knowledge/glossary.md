# Glossary — Performance Engineering Platform

> Langage ubiquitaire du projet. Tous les noms de classes, méthodes, variables, et
> termes dans le code DOIVENT correspondre à ce glossaire. En cas de doute, ce fichier
> fait autorité.

---

## Termes Domaine Core

| Terme | Définition | Classe/Interface |
|---|---|---|
| **Campaign** | Exécution complète d'un scénario (préparation + injection + assertions), matérialisée par `ExecutionState` puis `CampaignReport` | `ExecutionState`, `CampaignReport` |
| **Scenario** | Définition déclarative YAML d'une campagne. Immuable après parsing. | `ScenarioDefinition` |
| **ScenarioId** | Identifiant unique d'un scénario (value object) | `ScenarioId` |
| **ExecutionPlan** | Graphe d'exécution résolu à partir d'un Scenario. Représente le DAG et distingue les assertions d'intervalle. | `ExecutionPlan` |
| **ExecutionEngine** | Moteur unique LOCAL/DISTRIBUTED ; implémente `ExecuteScenarioUseCase` et `CancelExecutionUseCase` (ADR-037) | `ExecutionEngine` |
| **StepDispatcher** | Stratégie d'exécution d'un step : `LocalStepDispatcher` ou `RemoteStepDispatcher` | `StepDispatcher` |
| **DagPhaseExecutor** | Parcours DAG niveau par niveau, parallélisme Virtual Threads, skip des dépendances échouées | `DagPhaseExecutor` |
| **Output nommé** | Valeur d'un `TaskResult.outputs` accédée par nom contractuel (`result`, `assertion`, `p95Ms`, …) | `ExecutionContext.getOutput(...)` |
| **PersistentStore** | Implémentation par couches du store d'ExecutionContext (copy-on-write O(profondeur)) | `ExecutionContext.PersistentStore` |
| **ExecutionContext** | État partagé et immuable entre toutes les étapes d'une exécution | `ExecutionContext` |
| **ExecutionId** | Identifiant unique d'une exécution (value object) | `ExecutionId` |
| **Phase** | Une des trois étapes d'exécution : PREPARATION, INJECTION, ASSERTION | `Phase` (enum) |

---

## Termes Tâches

| Terme | Définition | Classe/Interface |
|---|---|---|
| **Task** | Unité atomique de travail dans un scénario | `StepDefinition` |
| **TaskId** | Identifiant unique d'une tâche dans un scénario. Correspond à `step.id()` | `TaskId` |
| **TaskExecutor** | Interface qui exécute une tâche. `execute(context, step)` + `getSupportedTaskName()` | `TaskExecutor` |
| **TaskResult** | Résultat immuable d'une tâche exécutée (succès ou échec) | `TaskResult` |
| **TaskType** | ~~Supprimé~~ — remplacé par `taskName` (String libre) dans `StepDefinition` et les annotations `@Preparation/@Injection/@Assertion` | — |
| **TaskExecutorRegistry** | Registre des TaskExecutor disponibles, résolution par `taskName` (String) | `TaskExecutorRegistry` |
| **StepDefinition** | Remplace `TaskDefinition` — ajoute `taskName`, `phase`, `requiredContexts` | `StepDefinition` |
| **taskName** | Nom libre de la task — matché avec `agent.supportedTaskNames` et les annotations | champ de `StepDefinition` |
| **ExecutionStep** | Nœud dans le DAG : wraps un Task avec ses dépendances résolues | `ExecutionStep` |

---

## Termes Injection / Load

| Terme | Définition | Classe/Interface |
|---|---|---|
| **Injection** | Phase de génération de charge (load injection) | `Phase.INJECTION` |
| **LoadModel** | Définition réutilisable du profil de charge (RAMP, CONSTANT, SPIKE...) | `LoadModel` |
| **LoadModelType** | Type de profil : RAMP, RAMP_UP_DOWN, CONSTANT, SPIKE, STAIR, SOAK, BURST, CUSTOM | `LoadModelType` (enum) |
| **Simulation** | Classe Gatling identifiée par son nom qualifié (String) | `GatlingRunConfig.simulationClass` |
| **InjectionResult** | Métriques agrégées d'une injection (p50/p90/p95/p99, errorRate, throughput) | `InjectionResult` |

---

## Termes Assertion

| Terme | Définition | Classe/Interface |
|---|---|---|
| **Assertion** | Vérification sur une métrique ou un état, déclarée comme `StepDefinition` de phase `ASSERTION` | `StepDefinition` + `AssertionResult` |
| **AssertionExecutor** | Interface qui évalue une assertion | `AssertionExecutor` |
| **AssertionResult** | Résultat d'une assertion : PASSED, FAILED, SKIPPED ou ERROR avec evidence | `AssertionResult` |
| **AssertionOperator** | Opérateur de comparaison : LT, LTE, GT, GTE, EQ, NEQ | `AssertionOperator` (enum) |
| **Evidence** | Preuve attachée au résultat d'une assertion | `Evidence` |

---

## Termes Transport / Distribution

| Terme | Définition | Classe/Interface |
|---|---|---|
| **ExecutionTransport** | Façade composite des 5 canaux de communication (ADR-028) | `ExecutionTransport` |
| **TaskChannel / SignalChannel / EventChannel / LifecycleChannel** | Ports étroits du transport | interfaces `transport.*` |
| **TransportType** | Type de transport : `KAFKA`, `RABBITMQ`, `HTTP`, `SOCKET`, `IN_MEMORY`, `CUSTOM` (gRPC exclu) | `TransportType` (enum) |
| **Agent** | Instance du runtime capable de recevoir et exécuter des tâches | `AgentRuntime` |
| **AgentId** | Identifiant unique d'un agent | `AgentId` |
| **AgentDescriptor** | Métadonnées d'un agent (id, `supportedTaskNames`, capacités, état, TTL) | `AgentDescriptor` |
| **AgentState** | État cycle de vie d'un agent : REGISTERING, IDLE, EXECUTING, DRAINING, OFFLINE | `AgentState` (enum) |
| **AgentSelector** | ~~Supprimé~~ — remplacé par la spécialisation déclarative (ADR-015) | — |
| **Orchestrator** | Instance DISTRIBUTED de rôle `ORCHESTRATOR` ; héberge `ExecutionEngine`, le registre d'agents et le reporting | rôle `RuntimeRole.ORCHESTRATOR` |
| **TaskMessage** | ~~Supprimé~~ — remplacé par `TaskExecutionRequest` (sans targetAgentId, avec PartialExecutionContext) | `TaskExecutionRequest` |
| **ExecutionEvent** | Événement du cycle de vie d'une exécution | `ExecutionEvent` |

---

## Termes Reporting

| Terme | Définition | Classe/Interface |
|---|---|---|
| **CampaignReport** | Rapport complet d'une campagne exécutée | `CampaignReport` |
| **Verdict** | Résultat global d'une campagne : SUCCESS, WARNING, FAILED | `Verdict` (enum) |
| **ReportRenderer** | Rendu d'un `CampaignReport` vers HTML/PDF/JSON | `ReportRenderer` |
| **ReportFileWriter** | Écriture locale des rapports dans `<output>/<executionId>/` | `ReportFileWriter` |

---

## Termes Runtime / Modes

| Terme | Définition | Config |
|---|---|---|
| **RuntimeMode** | Mode d'exécution de l'application | `runtime.mode` |
| **LOCAL** | Tout s'exécute dans le JVM local | `RUNTIME_MODE=LOCAL` ou `runtime.mode: LOCAL` |
| **DISTRIBUTED** | Orchestrator + agents distants | `RUNTIME_MODE=DISTRIBUTED` ou `runtime.mode: DISTRIBUTED` |
| **ORCHESTRATOR** | Rôle distribué qui planifie et agrège | `MODE=ORCHESTRATOR` (prioritaire sur properties) |
| **AGENT** | Rôle distribué qui exécute | `MODE=AGENT` (prioritaire sur properties) |

---

## Termes Plugin

| Terme | Définition | Classe/Interface |
|---|---|---|
| **Plugin** | JAR externe chargé au démarrage contenant des extensions custom | — |
| **PluginLoader** | Composant qui scanne `/plugins` et charge les JARs au démarrage | `PluginLoader` |
| **PluginRegistry** | Registre unifié des executors internes et externes, lookup par phase + name | `PluginRegistry` |
| **@Preparation** | Annotation marquant un `TaskExecutor` comme tâche de préparation | `@Preparation` |
| **@Injection** | Annotation marquant un `TaskExecutor` comme tâche d'injection de charge | `@Injection` |
| **@Assertion** | Annotation marquant un `TaskExecutor` comme tâche d'assertion post-injection | `@Assertion` |
| **platform-plugin-api** | Module Maven léger (interfaces + annotations) pour les développeurs de plugins | artifact Maven |


---

## Termes Agents Spécialisés (Évolution)

| Terme | Définition | Classe/Interface |
|---|---|---|
| **TaskSpecializationFilter** | Filtre côté agent : accepte ou ignore une `TaskExecutionRequest` selon `supportedTaskNames` | `TaskSpecializationFilter` |
| **TaskFilterResult** | Résultat du filtre : `Responsible` ou `NotResponsible` | `TaskFilterResult` (sealed) |
| **TaskExecutionRequest** | Remplace `TaskMessage` — demande de task sans `targetAgentId` (broadcast) | `TaskExecutionRequest` |
| **PartialExecutionContext** | Sous-ensemble de l'ExecutionContext transmis à l'agent (uniquement les `requiredContexts`) | `PartialExecutionContext` |
| **requiredContexts** | Liste des ids de steps dont les résultats sont nécessaires à un step donné | champ de `StepDefinition` |
| **StepDefinition** | Remplace `TaskDefinition` — ajoute `taskName`, `phase`, `requiredContexts` | `StepDefinition` |
| **taskName** | Nom libre de la task — matché avec `agent.supportedTaskNames` | champ de `StepDefinition` |
| **TaskCompletionPolicy** | Politique de complétion multi-agents : `FIRST_COMPLETE` ou `ALL_COMPLETE` | `TaskCompletionPolicy` (enum) |
| **TaskCorrelationTracker** | Suit la corrélation MessageId → claims → résultats (1:N) | `TaskCorrelationTracker` |
| **AgentAvailabilityChecker** | Vérifie la présence d'un agent compétent sans sélectionner | `AgentAvailabilityChecker` |
| **AgentLifecycleEvent** | Événement de cycle de vie agent (registered/heartbeat/deregistered/lost) sur un canal séparé (ADR-012) | `AgentLifecycleEvent` |
| **AgentLifecycleIngestion** | Pont transport lifecycle → registre d'agents côté orchestrateur (ADR-033) | `AgentLifecycleIngestion` |
| **AgentTtlMonitor** | Surveille les TTL et publie `AgentLost` pour déclencher le fast-fail | `AgentTtlMonitor` |
| **ResultSelectionPolicy** | Sélection déterministe du résultat multi-claim : `FIRST_COMPLETED` ou `LAST_COMPLETED` (ADR-034) | `ResultSelectionPolicy` (enum) |
| **TaskClaimedByAgent** | Event publié par l'agent quand il accepte une `TaskExecutionRequest` | `TaskClaimedByAgent` (record) |
| **TaskWorkInProgress** | Event périodique publié par l'agent pour signaler qu'il est actif | `TaskWorkInProgress` (record) |
| **TaskDispatched** | Event publié par l'orchestrateur après dispatch d'une task | `TaskDispatched` (record) |
| **ScenarioRestartSignal** | Signal broadcast de l'orchestrateur vers tous les agents — déclenche cleanup stateful | `ScenarioRestartSignal` (record) |
| **TaskAgentRuntime** | Runtime d'agent unique LOCAL/DISTRIBUTED (ADR-037) | `TaskAgentRuntime` |
| **LocalAgent** | ~~Supprimé~~ — remplacé par `TaskAgentRuntime` avec `registrationPort == null` | — |

---

## Termes à NE PAS utiliser (anti-glossaire)

| ❌ Terme à éviter | ✅ Terme correct |
|---|---|
| `TestSuite` | `Scenario` ou `Campaign` |
| `Runner` | `TaskExecutor` ou `ExecutionEngine` |
| `Config` (seul) | `ScenarioDefinition`, `LoadModel`, `AgentDescriptor` selon contexte |
| `Manager` | Préférer `Registry`, `Orchestrator`, `Engine` selon responsabilité |
| `Handler` (seul) | Préfixer : `CommandHandler`, `EventHandler`, `TaskHandler` |
| `TaskMessage` | Remplacé par `TaskExecutionRequest` |
| `TaskDefinition` | Remplacé par `StepDefinition` |
| `TaskType` (enum) | Remplacé par `taskName` (String) + annotations `@Preparation/@Injection/@Assertion` |
| `getSupportedType()` | Remplacé par `getSupportedTaskName()` |
| `AgentSelector` | Remplacé par la spécialisation déclarative — ne plus utiliser |
| `AgentAllocator` | Supprimé — ne plus utiliser |
| `TaskType` (enum) / `getSupportedType()` | Remplacés par `taskName` String + `getSupportedTaskName()` |
| `LocalExecutionEngine` / `RemoteExecutionEngine` | Supprimés — un seul `ExecutionEngine` + `StepDispatcher` (ADR-037) |
| `LocalAgent` / `DistributedAgentRuntime` | Supprimés — un seul `TaskAgentRuntime` (ADR-037) |
| `TaskMessage` | Remplacé par `TaskExecutionRequest` |
| `ReportPublisher` / `PublicationTarget` | Supprimés — rapport local uniquement (ADR-022) |
| `AGENT_TAGS` / `agentTags` | Remplacés par `AGENT_SUPPORTED_TASKS` / `agent.supported-tasks` (ADR-015) |
| `Spring Modulith` / `@ApplicationModuleListener` | Retirés — frontières vérifiées par `ModuleDependencyTest` ASM (ADR-035) |
| `Data` suffixé | Utiliser le nom du concept : `InjectionResult` pas `InjectionData` |

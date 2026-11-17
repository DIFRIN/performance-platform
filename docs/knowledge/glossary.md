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
| **ExecutionPlan** | Graphe d'exécution résolu à partir d'un Scenario. Représente le DAG, les trois phases et les service steps (`injectionServices`, `scenarioServices`). | `ExecutionPlan` |
| **ExecutionEngine** | Moteur unique LOCAL/DISTRIBUTED ; implémente `ExecuteScenarioUseCase` et `CancelExecutionUseCase` (ADR-037) | `ExecutionEngine` |
| **StepDispatcher** | Exécution d'un step via les ports de transport : `TransportStepDispatcher` (unique, ADR-059) ; `dispatch`, `startService`, `stopService` | `StepDispatcher` |
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
| **StepDefinition** | Remplace `TaskDefinition` — `id`, `taskName`, `phase` **ou** `during`, `parameters`, `dependsOn`, `ref`, `drain`, `fanOut`, `requiredContexts`, `timeout`, `retryPolicy` | `StepDefinition` |
| **Action step** | Étape qui s'exécute jusqu'au bout et déclare `phase` | `StepDefinition.isService() == false` |
| **Service step** | Étape avec une durée de vie (`during: injection \| scenario`), démarrée puis arrêtée par l'orchestrateur ; résultat publié au STOP (ADR-054) | `StepDefinition.during` |
| **ServiceTaskExecutor / ServiceControl** | Contrat d'une service step (`executeService`) et contrôle de son STOP | `ServiceTaskExecutor`, `ServiceControl` |
| **fanOut** | Nombre d'agents autorisés à exécuter une étape : `one` (défaut) ou `all` (ADR-055) | `FanOut` |
| **ref** | Référence de première classe vers un autre step, utilisée pour lire son résultat (remplace `parameters.refTaskId`) | `StepDefinition.ref` |
| **taskName** | Nom libre de la task — matché avec `agent.supportedTaskNames` et les annotations | champ de `StepDefinition` |
| **ExecutionStep** | Nœud dans le DAG : wraps un Task avec ses dépendances résolues | `ExecutionStep` |

---

## Termes Injection / Load

| Terme | Définition | Classe/Interface |
|---|---|---|
| **Injection** | Phase de génération de charge (load injection) | `Phase.INJECTION` |
| **WorkloadPlan** | Plan de charge typé et immuable d'une injection (modèle `open`/`closed`, scope, stages), déclaré **inline** dans `parameters.workload` (ADR-064) | `WorkloadPlan` |
| **Stage** | Étape d'un plan : `Ramp`, `Hold`, `Burst`, `Pause` ; `measured: false` = warm-up exclu des métriques | `Stage` (sealed) |
| **LoadScope** | `per-agent` (chaque agent joue le plan) ou `global` (le plan est le total, réparti par part) | `LoadScope` |
| **GrantShare** | Part `(index, count, fraction)` accordée à un agent par le grant (ADR-067) | `GrantShare` |
| **Preset** | Forme courte `loadModel: {type, parameters}` développée en `WorkloadPlan` au parsing | `LoadPresets` |
| **INVALID** | Verdict : l'expérience décrite n'a pas été appliquée (contrôle d'intégrité en échec) ; prime sur `FAILED` (ADR-069) | `Verdict.INVALID` |
| **Intégrité** | Famille de contrôles automatiques d'une injection : déviation d'arrivée, perte d'agent, run incomplet, saturation | `IntegrityCheckFactory` |
| **Worker d'injection** | JVM fille démarrée par l'agent pour un run d'injection (ADR-066) | `InjectionWorkerMain` |
| **Simulation** | Classe Gatling identifiée par son nom qualifié (String) | `GatlingRunConfig.simulationClass` |
| **InjectionResult** | Métriques agrégées d'une injection (p50/p90/p95/p99, errorRate, throughput) | `InjectionResult` |

---

## Termes Assertion

| Terme | Définition | Classe/Interface |
|---|---|---|
| **Assertion** | Vérification sur une métrique ou un état, déclarée comme `StepDefinition` de phase `ASSERTION` | `StepDefinition` + `AssertionSummary` |
| **AssertionExecutor** | Interface qui évalue une assertion | `AssertionExecutor` |
| **AssertionSummary** | Résumé d'une assertion : verdict, `Check[]`, `SloStatement`, `Rollup` | `AssertionSummary` |
| **Check** | Verdict d'un critère unique (métrique observée + attente) | `Check` |
| **Observation** | Fait mesuré, jamais un jugement | `Observation` |
| **SloStatement** | Intention SLI/SLO textuelle recopiée dans le rapport | `SloStatement` |
| **AssertionOperator** | Opérateur de comparaison : LT, LTE, GT, GTE, EQ, NEQ | `AssertionOperator` (enum) |

---

## Termes Transport / Distribution

| Terme | Définition | Classe/Interface |
|---|---|---|
| **ExecutionTransport** | Façade composite des 5 canaux de communication (ADR-028) | `ExecutionTransport` |
| **TaskChannel / SignalChannel / EventChannel / LifecycleChannel** | Ports étroits du transport | interfaces `transport.*` |
| **TransportType** | Type de transport : `KAFKA`, `HTTP`, `IN_MEMORY`, `CUSTOM` (ADR-056 ; RabbitMQ et socket passent par `CUSTOM`) | `TransportType` (enum) |
| **Agent** | Instance du runtime capable de recevoir et exécuter des tâches | `AgentRuntime` |
| **AgentId** | Identifiant unique d'un agent | `AgentId` |
| **AgentDescriptor** | Métadonnées d'un agent (id, `supportedTaskNames`, capacités, état, TTL) | `AgentDescriptor` |
| **AgentState** | État cycle de vie d'un agent : REGISTERING, IDLE, EXECUTING, DRAINING, OFFLINE | `AgentState` (enum) |
| **AgentSelector** | ~~Supprimé~~ — remplacé par la spécialisation déclarative (ADR-015) | — |
| **Orchestrator** | Instance DISTRIBUTED de rôle `ORCHESTRATOR` ; héberge `ExecutionEngine`, le registre d'agents et le reporting. **Une seule par déploiement** (ADR-058) | rôle `RuntimeRole.ORCHESTRATOR` |
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
| **PerformanceAssessment** | Verdict de rapport structuré en six sections (contexte, charge, SLI/SLO, technique, analyse, validation) | `PerformanceAssessment` |
| **ReviewState** | État de revue humaine du rapport : `NONE`, `REQUIRED`, `ACCEPTED`, `REJECTED` (ADR-051) | `ReviewState` (enum) |

---

## Termes Planification

| Terme | Définition | Classe/Interface |
|---|---|---|
| **ScheduledRun** | Run planifié d'un scénario avec son échéance, son statut et le motif de planification | `ScheduledRun` |
| **ScheduleSpec** | Grammaire de planification : immédiat, one-shot (`in 10m`, date ISO) ou cron `cron:0 0 2 * * *` | `ScheduleSpec` |
| **ScheduleKind** | Type de planification (`IMMEDIATE`, `ONCE`, `CRON`) | `ScheduleKind` (enum) |
| **ScheduledRunStatus** | `PENDING`, `COMPLETED`, `FAILED`, `CANCELLED` | `ScheduledRunStatus` (enum) |

---

## Termes Runtime / Modes

| Terme | Définition | Config |
|---|---|---|
| **RuntimeMode** | Mode d'exécution de l'application | `runtime.mode` |
| **LOCAL** | Le moteur distribué avec le transport en mémoire et un agent embarqué, dans une seule JVM (ADR-059) | `RUNTIME_MODE=LOCAL` ou `runtime.mode: LOCAL` |
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
| **requiredContexts** | Override explicite des ids de steps dont les résultats sont transmis à un step ; vide par défaut = `dependsOn` + `ref` (ADR-053) | champ de `StepDefinition` |
| **StepDefinition** | Remplace `TaskDefinition` — `id`, `taskName`, `phase` **ou** `during`, `parameters`, `dependsOn`, `ref`, `drain`, `fanOut`, `requiredContexts`, `timeout`, `retryPolicy` | `StepDefinition` |
| **taskName** | Nom libre de la task — matché avec `agent.supportedTaskNames` | champ de `StepDefinition` |
| **TaskGrantSignal** | Grant accordé (ou refusé) en réponse à un claim ; un agent n'exécute qu'après un grant accordé (ADR-055) | `TaskGrantSignal` (record) |
| **TaskCorrelationTracker** | Suit la corrélation MessageId → claims → grants → résultats (1:N) | `TaskCorrelationTracker` |
| **AgentAvailabilityChecker** | Vérifie la présence d'un agent compétent sans sélectionner | `AgentAvailabilityChecker` |
| **AgentLifecycleEvent** | Événement de cycle de vie agent (registered/heartbeat/deregistered/lost) sur un canal séparé (ADR-012) | `AgentLifecycleEvent` |
| **AgentLifecycleIngestion** | Pont transport lifecycle → registre d'agents côté orchestrateur (ADR-033) | `AgentLifecycleIngestion` |
| **AgentTtlMonitor** | Surveille les TTL et publie `AgentLost` pour déclencher le fast-fail | `AgentTtlMonitor` |
| **TaskClaimedByAgent** | Event publié par l'agent quand il revendique une `TaskExecutionRequest` | `TaskClaimedByAgent` (record) |
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
| `Config` (seul) | `ScenarioDefinition`, `WorkloadPlan`, `AgentDescriptor` selon contexte |
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
| `LocalStepDispatcher` / `RemoteStepDispatcher` | Supprimés — un seul `TransportStepDispatcher` (ADR-059) |
| `LocalLifecycleDispatcher` / `RemoteLifecycleDispatcher` | Supprimés — un seul `TransportLifecycleDispatcher` (ADR-059) |
| `OutcomeEventPublisher` | Supprimé — `TransportExecutionEventHandler` publie les events (ADR-059) |
| `parameters.linkedTo` | Remplacé par `during` (service step, ADR-054) |
| `parameters.refTaskId` | Remplacé par le champ `ref` (ADR-053) |
| `completion-policy` / `result-selection-policy` | Supprimés — `fanOut` par étape (ADR-055) |
| `loadModels` (registre nommé) | Supprimé — `workload` inline dans les paramètres (ADR-053, ADR-064) |
| `LoadModel` / `LoadModelType` | Supprimés du domaine — `WorkloadPlan` ; `loadModel` n'est plus qu'un preset YAML (ADR-064) |
| `TaskMessage` | Remplacé par `TaskExecutionRequest` |
| `ReportPublisher` / `PublicationTarget` | Supprimés — rapport local uniquement (ADR-022) |
| `AGENT_TAGS` / `agentTags` | Remplacés par `AGENT_SUPPORTED_TASKS` / `agent.supported-tasks` (ADR-015) |
| `Spring Modulith` / `@ApplicationModuleListener` | Retirés — frontières vérifiées par `ModuleDependencyTest` ASM (ADR-035) |
| `Data` suffixé | Utiliser le nom du concept : `InjectionResult` pas `InjectionData` |

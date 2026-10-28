# Catalogue des composants — Performance Engineering Platform

> Vue composant par composant : responsabilité, collaborations, cycle de vie et points
> d'attention. Complète `architecture.md` (vue d'ensemble et mécanismes) et les specs
> par domaine. Mis à jour : 2026-10-20.

```mermaid
sequenceDiagram
    autonumber
    participant EE as ExecutionEngine
    participant DISP as RemoteStepDispatcher
    participant TX as TaskChannel
    participant AG as TaskAgentRuntime
    participant EXEC as TaskExecutor
    participant EV as EventChannel
    participant H as TransportExecutionEventHandler
    participant TRK as TaskCorrelationTracker

    EE->>DISP: dispatch(step, context)
    DISP->>TX: dispatchTask(request)
    TX->>AG: TaskExecutionRequest (broadcast)
    AG->>EXEC: execute(context, step)
    EXEC-->>AG: TaskResult
    AG->>EV: TASK_COMPLETED
    EV->>H: onEvent
    H->>TRK: onCompleted(agentId, result)
    TRK-->>DISP: completion signal
```

---

## 1. Entrée et assemblage (`platform-app`)

| Composant | Rôle | Collaborateurs |
|---|---|---|
| `PerformancePlatformApplication` | `main()` ; choisit `WebApplicationType` (SERVLET ou NONE pour un profil agent) avant Spring ; lance l'application | `RuntimeConfigResolver`, `SpringApplication` |
| `RuntimeConfigResolver` | Source unique de résolution mode/rôle/transport (env > CLI > property > défaut) ; parsing partagé | `Environment`, args CLI |
| `RuntimeConfigEnvironmentPostProcessor` | Réinjecte `RUNTIME_MODE`, `MODE`, `TRANSPORT_TYPE` comme properties avant les `@ConditionalOnProperty` | `Environment` |
| `RuntimeModeResolver` | Façade Spring historique autour de `RuntimeConfigResolver` | `Environment` |
| `RuntimeAssemblyConfiguration` | Composition root mince : `TaskExecutorLookup`, parser/validateur DSL, read-model, fallback `RestClient.Builder` | `TaskExecutorRegistry`, `ExecutionRepository` |
| `ExecutionUseCaseConfiguration` | Expose `ListExecutionsUseCase`, `DeleteExecutionUseCase`, `ExecutionProgressCalculator` | `ExecutionRepository` |
| `AgentRuntimeConfiguration` | Crée `TaskAgentRuntime` LOCAL (tous les executors) ou DISTRIBUTED (`agent.supported-tasks`) + `SmartLifecycle` | `InMemoryExecutionTransport`/`ExecutionTransport`, `TaskExecutor` |
| `PluginBootstrap` | Charge les JARs de `platform.plugins.dir` au démarrage et logge le résultat | `PluginLoader`, `PluginProperties` |
| `CliScenarioRunner` | Mode headless `--scenario=` : parse, exécute, imprime un résumé, code 0/1/2 | `ScenarioParsingUseCase`, `ExecuteScenarioUseCase`, `GetExecutionStatusUseCase` |
| `SecurityConfiguration` | Chaîne de sécurité : permissive si `platform.security.enabled=false`, sinon JWT + rôles sur `/api/**` | `Environment`, `HttpSecurity` |
| `WebUiConfiguration` | Sert l'IHM statique si `web.ui.enabled=true` | `WebMvcConfigurer` |

Contrôleurs REST (tous `@ConditionalOnWebApplication(SERVLET)`) :
`ScenarioController`, `ScenarioUploadController`, `ExecutionController`,
`AgentController`, `ReportController`.

---

## 2. Domaine (`platform-domain`)

| Composant | Rôle |
|---|---|
| `ScenarioDefinition` | Définition immuable d'un scénario (steps, loadModels, mode, metadata) |
| `StepDefinition` | Tâche déclarative : `id`, `taskName`, `phase`, `parameters`, `dependsOn`, `requiredContexts`, `timeout`, `retryPolicy` |
| `ExecutionPlan` | Plan résolu : steps par phase + `assertionIntervalSteps` + contexte initial |
| `ExecutionStep` | Step + dépendances + `dagLevel` + clés de contexte requises |
| `ExecutionContext` | Store immuable `taskId → agentId → TaskResult`, outputs nommés, `PersistentStore` |
| `PartialExecutionContext` | Sous-ensemble transmis aux agents (`requiredContexts`), outputs nommés |
| `ExecutionState` | État persisté : statut global, statuts de phases, contexte, timestamps |
| `TaskResult` | Résultat immuable : `taskName`, statut, durée, outputs, erreur |
| `TaskStatus` | `SUCCESS`, `FAILED`, `SKIPPED`, `TIMEOUT` |
| `RetryPolicy` | Politique de retry avec backoff exponentiel |
| `TaskCompletionPolicy` | `FIRST_COMPLETE` ou `ALL_COMPLETE` |
| `AgentDescriptor` / `AgentHeartbeat` / `AgentState` | Identité, heartbeat, cycle de vie agent |
| `InjectionResult` | KPIs Gatling (`OUTPUT_KEY = "result"`) |
| `AssertionResult` / `AssertionSummary` / `Evidence` | Résultat d'assertion (`OUTPUT_KEY = "assertion"`) |
| Events | `ScenarioStarted/Finished/Cancelled`, `PhaseStarted/Completed`, `TaskDispatched/ClaimedByAgent/Completed/Failed`, `AgentRegistered/Heartbeat/Lost`, `ScenarioRestartSignal`, `ExecutionLifecycleSignal`, `ReportGenerated` |

---

## 3. Ports et use cases (`platform-application`)

| Composant | Rôle |
|---|---|
| `ExecuteScenarioUseCase` | Démarrer une exécution (implémenté par `ExecutionEngine`) |
| `CancelExecutionUseCase` | Annuler une exécution (implémenté par `ExecutionEngine`) |
| `GetExecutionStatusUseCase` | Read-model de statut/progression (`GetExecutionStatusService`) |
| `ListExecutionsUseCase` / `DeleteExecutionUseCase` | Listing et suppression (`ListExecutionsService`, `DeleteExecutionService`) |
| `ScenarioParsingUseCase` | Parse + valide un YAML (`DefaultScenarioParsingService`) |
| `GenerateReportUseCase` | Port défini, **sans implémentation** actuellement |
| `ExecutionRepository` | Persistance des états et résultats de tâches |
| `AgentRegistryPort` | Suivi du cycle de vie des agents (implémenté côté agent-runtime) |
| `EndpointResolver` / `Endpoint` | Résolution framework-free des cibles HTTP (ADR-036) |
| `LifecycleSignalHandler` | Réception START/STOP (implémenté par `TaskAgentRuntime`) |
| `ExecutionConfig` | Timeouts et politiques (créé depuis `ExecutionEngineProperties`) |
| `ExecutionProgressCalculator` | Calcul de progression à partir de `ExecutionState` |

---

## 4. DSL (`platform-scenario-dsl`)

| Composant | Rôle |
|---|---|
| `YamlScenarioParser` | SnakeYAML + Jackson ; rejette les clés inconnues ; produit `ScenarioDefinition` |
| `DefaultScenarioValidator` | Valide ids, phases, dépendances, cycles DAG, `requiredContexts`, `loadModels` |
| `DagCycleDetector` | Détection de cycle |
| `ValidationResult/Error/Warning` | Résultat de validation détaillé |
| `DefaultLoadModelRegistry` | Registre en mémoire des load models |
| `DefaultScenarioParsingService` | Orchestration parse → validate ; erreurs bloquantes seulement |

---

## 5. Moteur (`platform-execution-engine`)

| Composant | Rôle | Collaborateurs |
|---|---|---|
| `ExecutionEngine` | Moteur unique : initialise, exécute les 3 phases, calcule le verdict, persiste, publie les events | `ExecutionPlanBuilder`, `DagPhaseExecutor`, `ExecutionRepository`, `OutcomeEventPublisher`, `ExecutionLifecycleDispatcher` |
| `DefaultExecutionPlanBuilder` | Construit `ExecutionPlan`, sépare assertions point-in-time vs `linkedTo` | `DagLevelCalculator` |
| `DagLevelCalculator` | Calcule les niveaux DAG, détecte les cycles |
| `DagPhaseExecutor` | Parcours DAG par niveau, Virtual Threads, skip des dépendances non satisfaites, annulation | `StepDispatcher`, `ExecutionLifecycleDispatcher` |
| `StepDispatcher` | Stratégie d'exécution d'un step | — |
| `LocalStepDispatcher` | Appel direct de l'executor + retry | `TaskExecutorLookup`, `RetryExecutor` |
| `RemoteStepDispatcher` | Attente d'agent, partial context, broadcast, attente de complétion, sélection | `TaskChannel`, `AgentAvailabilityChecker`, `TaskCorrelationTracker`, `ExecutionConfig` |
| `TransportExecutionEventHandler` | Consomme les événements agent, alimente tracker/repository, publie les events | `EventChannel`, `TaskCorrelationTracker`, `ExecutionRepository` |
| `PartialContextBuilder` | Extrait les outputs nommés requis du contexte complet | — |
| `DefaultTaskCorrelationTracker` | Suit claims/completions/échecs par `MessageId`, fast-fail sur perte d'agent | — |
| `DefaultAgentAvailabilityChecker` | Attend qu'un agent compétent soit enregistré | `AgentRegistryPort` |
| `DefaultRetryExecutor` | Backoff exponentiel plafonné | — |
| `LocalLifecycleDispatcher` / `RemoteLifecycleDispatcher` | START/STOP en local / via transport | `TaskAgentRuntime` / `SignalChannel` |
| `LocalOutcomeEventPublisher` | Publie les events de tâche en LOCAL et persiste les résultats | `ApplicationEventPublisher`, `ExecutionRepository` |

---

## 6. Runtime agent (`platform-agent-runtime`)

| Composant | Rôle |
|---|---|
| `TaskAgentRuntime` | Runtime unique : connexion, enregistrement optionnel, réception, exécution, drain, restart |
| `TaskExecutionPipeline` | Exécution d'une requête : claim, progression, résolution du `TaskExecutor`, publication du résultat |
| `DefaultTaskSpecializationFilter` | Décide si l'agent est responsable d'un `taskName` |
| `TransportAgentRegistration` | Publie registration/heartbeat/deregistration sur le `LifecycleChannel` |
| `AgentLifecyclePayloadCodec` | Encodage/décodage typé de `AgentDescriptor`/`AgentHeartbeat` |
| `HeartbeatScheduler` | Heartbeat périodique (Virtual Thread), TTL = 3 × intervalle |
| `AgentLifecycleIngestion` | Orchestrateur : transport lifecycle → registre |
| `InMemoryAgentRegistry` | Registre thread-safe des agents |
| `AgentTtlMonitor` | Éviction TTL + publication `AgentLost` |
| `AgentRegistryConfiguration` | Câblage conditionnel DISTRIBUTED + ORCHESTRATOR |
| `ScenarioRestartHandler` | Cleanup stateful + annulation des tâches + retour IDLE |
| `DefaultLifecycleSignalHandler` | START/STOP des tâches et assertions d'intervalle |

---

## 7. Transport (`platform-transport`)

Canaux : `TaskChannel`, `SignalChannel`, `EventChannel`, `LifecycleChannel`,
`ConnectionLifecycle` ; façade `ExecutionTransport`.

| Implémentation | Composants | Notes |
|---|---|---|
| `InMemoryExecutionTransport` | handlers synchrones | LOCAL/tests |
| `KafkaExecutionTransport` | `DynamicKafkaListenerRegistry`, `KafkaMessageCodec`, `KafkaTransportBeans` | consumer group par agent (ADR-009) |
| `RabbitMQExecutionTransport` | `RabbitMQConsumerManager`, `RabbitMQMessageCodec` | exchanges FANOUT, ack manuel |
| `HttpExecutionTransport` | `HttpEventCallbackController` | POST vers agents, callbacks `/api/v1/events` |
| `SocketExecutionTransport` | `SocketConnectionRegistry` | TCP persistant, best-effort |

`TransportConfiguration` sélectionne l'implémentation par `@ConditionalOnProperty` ;
aucun `getType()`.

---

## 8. Infrastructure (`platform-infrastructure`)

### Exécuteurs (`TaskExecutor`)
`DatabaseTaskExecutor` (`database`), `DockerTaskExecutor` (`docker`),
`FilesystemTaskExecutor` (`filesystem`), `HttpClientTaskExecutor` (`http-client`),
`KafkaProducerTaskExecutor` (`kafka-producer`), `KafkaConsumerTaskExecutor`
(`kafka-consumer`), `MockServerTaskExecutor` (`mock-server`), `ShellTaskExecutor`
(`shell`).

### Registre et plugins
`TaskExecutorRegistry`/`DefaultTaskExecutorRegistry` (beans Spring),
`PluginLoader`, `DefaultPluginLoader`, `AnnotationScanner`, `PluginDescriptor`,
`PluginLoadResult`, `PluginRegistry`/`DefaultPluginRegistry`, `UnsupportedTaskNameException`.

### Persistance
`JpaExecutionRepository`, `ExecutionStateEntity`, `TaskResultEntity`,
`ExecutionStateJpaRepository`, `TaskResultJpaRepository`, mappers, `JpaConfiguration`,
`DatasourceConfiguration`, `PlatformDatasourcesProperties`.

### Cibles et clusters
`HttpTargetRegistry`, `HttpTargetProperties`, `PlatformHttpTargetsProperties`,
`KafkaClusterRegistry`, `KafkaClusterProperties`, `PlatformKafkaProperties`.

---

## 9. Capacités

| Module | Composants |
|---|---|
| `platform-injection-gatling` | `GatlingTaskExecutor`, `GatlingRunner`/`DefaultGatlingRunner`, `SimulationInjectionHolder`, `LoadModelTranslator`/`DefaultLoadModelTranslator`, `GatlingResultParser`, `ProtocolSupportInfo` |
| `platform-assertion` | `DatabaseAssertionExecutor`, `FileAssertionExecutor`, `GatlingMetricAssertionExecutor`, `HttpMockAssertionExecutor`, `KafkaAssertionExecutor`, `WireMockAssertionExecutor`, `AssertionResultMapper`, `MetricExtractor` |
| `platform-reporting` | `DefaultReportEngine`, `VerdictCalculator`, `HtmlReportRenderer`, `PdfReportRenderer`, `JsonReportRenderer`, `ReportFileWriter`, `ReportProperties` |
| `platform-observability` | `ObservabilityEventListener`, `MicrometerExecutionMetrics`, `ExecutionContextMdcFilter`, `ObservabilityConfiguration` |

---

## 10. Relations critiques et pièges

1. **`ExecutionEngine` ne connaît jamais le transport.** Toute dépendance réseau doit
   passer par `StepDispatcher` (remote) ; le handler d'événements est un collaborateur
   séparé.
2. **`TaskAgentRuntime` ne connaît jamais le mode.** `registrationPort == null` est le
   seul discriminant LOCAL.
3. **Aucun `if/switch` sur le type de transport ou le mode.** Sélection par beans
   conditionnels.
4. **Les outputs sont nommés.** Ne pas revenir à `get(Class<T>)` (supprimé du contexte
   orchestrateur) : utiliser `getOutput`/`getFirstOutput`.
5. **Le store est persistant.** Ne pas copier le store complet dans un executor ; utiliser
   `context.with(...)`.
6. **Le timeout du step borne les clients I/O.** Les exécuteurs Kafka/HTTP configurent
   leurs timeouts à partir de `StepDefinition.timeout`.
7. **Gaps connus** (documentés, non implémentés) :
   - `PluginRegistry` découvre les JARs mais n'est pas raccordé au moteur/agent
     (`TaskExecutorRegistry` ne collecte que les beans Spring) ;
   - le retry distribué n'est pas appliqué par `TaskExecutionPipeline` (une seule
     tentative) ;
   - `ReportFileWriter` n'est pas câblé à `ReportGenerated` (aucune écriture disque
     automatique) et `GenerateReportUseCase` n'a pas d'implémentation ;
   - le transport HTTP n'expose pas d'endpoint entrant de tâches et l'AGENT n'a pas de
     serveur web (ADR-019) : il sert au dispatch sortant + callbacks d'événements.

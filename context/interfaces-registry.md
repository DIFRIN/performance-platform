# Interfaces Registry

> Index des statuts de toutes les interfaces et classes publiques du projet.
> **Les signatures complètes sont dans les PDRs** (`pdr/PDR-XXX.md`), pas ici.
> Ce fichier répond uniquement à : "Est-ce que X est déjà implémenté ?"
>
> Mis à jour par : System Designer (PLANNED), Developer (IN PROGRESS), Reviewer (STABLE).
> Lu par : tout agent qui veut éviter de créer un doublon ou de diverger sur un nom.

---

## Légende

| Symbole | Statut | Signification |
|---|---|---|
| ⬜ | PLANNED | Dans les specs/PDRs, pas encore codé |
| 🔄 | IN PROGRESS | En cours dans une Issue active |
| ✅ | STABLE | Implémenté + APPROVED par le Reviewer |
| ⚠️ | BREAKING | ADR en cours — ne pas utiliser |

---

## platform-domain

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `ExecutionContext` | ⬜ PLANNED | — | — |
| `ExecutionId` | ⬜ PLANNED | — | — |
| `ExecutionPlan` | ⬜ PLANNED | — | — |
| `ExecutionStep` | ⬜ PLANNED | — | — |
| `ExecutionState` | ⬜ PLANNED | — | — |
| `Phase` | ⬜ PLANNED | — | — |
| `ScenarioDefinition` | ⬜ PLANNED | — | — |
| `ScenarioId` | ⬜ PLANNED | — | — |
| `TaskDefinition` | ❌ REMOVED | ADR-008 | — | Remplacé par `StepDefinition` |
| `TaskId` | ⬜ PLANNED | — | — |
| `TaskType` | ❌ REMOVED | ADR-008 | — | Remplacé par `taskName` String + annotations |
| `StepDefinition` | ⬜ PLANNED | — | — | Remplace `TaskDefinition` |
| `TaskResult` | ⬜ PLANNED | — | — |
| `TaskExecutionRequest` | ⬜ PLANNED | — | — | (remplace TaskMessage) |
| `PartialExecutionContext` | ⬜ PLANNED | — | — |
| `StepDefinition` | ⬜ PLANNED | — | — | (remplace TaskDefinition) |
| `TaskCompletionPolicy` | ⬜ PLANNED | — | — |
| `ScenarioRestartSignal` | ⬜ PLANNED | — | — |
| `TaskClaimedByAgent` | ⬜ PLANNED | — | — |
| `TaskWorkInProgress` | ⬜ PLANNED | — | — |
| `TaskDispatched` | ⬜ PLANNED | — | — |
| `TaskStatus` | ⬜ PLANNED | — | — |
| `LoadModel` | ⬜ PLANNED | — | — |
| `LoadModelType` | ⬜ PLANNED | — | — |
| `AgentSelector` | ❌ REMOVED | ADR-008 | — |
| `InjectionResult` | ⬜ PLANNED | — | — |
| `AssertionResult` | ⬜ PLANNED | — | — |
| `AssertionOperator` | ⬜ PLANNED | — | — |
| `AssertionStatus` | ⬜ PLANNED | — | — |
| `Evidence` | ⬜ PLANNED | — | — |
| `Verdict` | ⬜ PLANNED | — | — |
| `AgentId` | ⬜ PLANNED | — | — |
| `AgentDescriptor` | ⬜ PLANNED | — | — | (évolué : +supportedTaskNames, +httpCallbackUrl, +registrationTtl; -tags) |
| `AgentState` | ⬜ PLANNED | — | — |
| `AgentCapabilities` | ⬜ PLANNED | — | — | (simplifié : version seulement) |
| _(Domain Events — 16 records)_ | ⬜ PLANNED | — | — |

## platform-application (Ports)

| Interface | Statut | PDR | Issue |
|---|---|---|---|
| `ExecuteScenarioUseCase` | ⬜ PLANNED | — | — |
| `ScenarioParsingUseCase` | ⬜ PLANNED | — | — |
| `GetExecutionStatusUseCase` | ⬜ PLANNED | — | — |
| `CancelExecutionUseCase` | ⬜ PLANNED | — | — |
| `GenerateReportUseCase` | ⬜ PLANNED | — | — |
| `ExecutionRepository` | ⬜ PLANNED | — | — |
| `AgentRegistryPort` | ⬜ PLANNED | — | — |
| `ReportPublisherPort` | ⬜ PLANNED | — | — |

## platform-execution-engine

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `ExecutionEngine` | ⬜ PLANNED | — | — |
| `ExecutionPlanBuilder` | ⬜ PLANNED | — | — |
| `LocalExecutionEngine` | ⬜ PLANNED | — | — |
| `RemoteExecutionEngine` | ⬜ PLANNED | — | — |
| `RetryPolicy` | ⬜ PLANNED | — | — |

## platform-transport

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `ExecutionTransport` ⚡ | ⬜ PLANNED | — | — |
| `TaskMessage` | ❌ REPLACED | ADR-008 | — | (remplacé par TaskExecutionRequest) |
| `ExecutionEvent` | ⬜ PLANNED | — | — |
| `SocketExecutionTransport` | ⬜ PLANNED | — | — |
| `RabbitMQExecutionTransport` | ⬜ PLANNED | — | — |
| `KafkaExecutionTransport` | ⬜ PLANNED | — | — |
| `GrpcExecutionTransport` | ⬜ PLANNED | — | — |

## platform-injection-gatling

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `GatlingTaskExecutor` | ⬜ PLANNED | — | — |
| `GatlingRunner` | ⬜ PLANNED | — | — |
| `GatlingResultParser` | ⬜ PLANNED | — | — |
| `LoadModelTranslator` | ⬜ PLANNED | — | — |

## platform-assertion

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `AssertionExecutor` | ⬜ PLANNED | — | — |
| `AssertionExecutorRegistry` | ⬜ PLANNED | — | — |
| `GatlingMetricAssertionExecutor` | ⬜ PLANNED | — | — |
| `DatabaseAssertionExecutor` | ⬜ PLANNED | — | — |
| `KafkaAssertionExecutor` | ⬜ PLANNED | — | — |
| `HttpMockAssertionExecutor` | ⬜ PLANNED | — | — |
| `FileAssertionExecutor` | ⬜ PLANNED | — | — |

## platform-reporting

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `ReportEngine` | ⬜ PLANNED | — | — |
| `ReportRenderer` | ⬜ PLANNED | — | — |
| `ReportPublisher` ⚡ | ⬜ PLANNED | — | — |
| `CampaignReport` | ⬜ PLANNED | — | — |
| `ConfluenceReportPublisher` | ⬜ PLANNED | — | — |
| `S3ReportPublisher` | ⬜ PLANNED | — | — |
| `GitReportPublisher` | ⬜ PLANNED | — | — |

## platform-agent-runtime

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `AgentRuntime` | ⬜ PLANNED | — | — |
| `AgentRegistrationPort` | ⬜ PLANNED | — | — |
| `AgentRegistry` | ⬜ PLANNED | — | — |
| `AgentAllocator` | ❌ REMOVED | ADR-008 | — |
| `AgentAvailabilityChecker` | ⬜ PLANNED | — | — |
| `TaskSpecializationFilter` | ⬜ PLANNED | — | — |
| `TaskFilterResult` | ⬜ PLANNED | — | — |
| `TaskCorrelationTracker` | ⬜ PLANNED | — | — |
| `LocalAgent` | ⬜ PLANNED | — | — |

---

> ⚡ = Interface publique critique — toute modification requiert un ADR.
> Les colonnes PDR et Issue sont remplies par le System Designer au moment de la création des PDRs.

---

## platform-plugin-api (module léger — 0 framework)

| Classe / Annotation | Statut | PDR | Issue |
|---|---|---|---|
| `@Preparation` ⚡ | ⬜ PLANNED | — | — |
| `@Injection` ⚡ | ⬜ PLANNED | — | — |
| `@Assertion` ⚡ | ⬜ PLANNED | — | — |

## platform-infrastructure (Plugin System)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `PluginLoader` | ⬜ PLANNED | — | — |
| `PluginLoadResult` | ⬜ PLANNED | — | — |
| `PluginRegistry` | ⬜ PLANNED | — | — |

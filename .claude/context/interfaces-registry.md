# Interfaces Registry

> Index des statuts de toutes les interfaces et classes publiques du projet.
> **Les signatures complètes sont dans les PDRs** (`.claude/pdr/PDR-XXX.md`), pas ici.
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
| ❌ | REMOVED | Supprimé — ne plus utiliser |

> Décisions de cadrage : `TaskType` supprimé (→ `String taskName`), gRPC non implémenté,
> `platform-infrastructure` séparé en 4 packages (.executor/.plugin/.persistence/.publisher).

---

## platform-domain

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `ExecutionId` | ✅ STABLE | PDR-001 | ISSUE-001 |
| `ScenarioId` | ✅ STABLE | PDR-001 | ISSUE-001 |
| `TaskId` | ✅ STABLE | PDR-001 | ISSUE-001 |
| `AgentId` | ✅ STABLE | PDR-001 | ISSUE-001 |
| `MessageId` | ✅ STABLE | PDR-001 | ISSUE-001 |
| `EventId` | ✅ STABLE | PDR-001 | ISSUE-001 |
| `SignalId` | ✅ STABLE | PDR-001 | ISSUE-001 |
| `ReportId` | ✅ STABLE | PDR-001 | ISSUE-001 |
| `Phase` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `ExecutionMode` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `TaskStatus` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `PhaseStatus` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `ExecutionStatus` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `AgentState` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `TaskCompletionPolicy` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `LoadModelType` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `AssertionOperator` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `AssertionStatus` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `Verdict` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `ReportFormat` | ✅ STABLE | PDR-001 | ISSUE-002 |
| `PublicationTarget` | ❌ REMOVED | PDR-001 | — (noms d'outils → déplacé dans platform-reporting, ISSUE-065) |
| `TransportType` | ❌ REMOVED | PDR-001 | — (noms de protocoles → déplacé dans platform-transport, ISSUE-028) |
| `ScenarioDefinition` | ✅ STABLE | PDR-001 | ISSUE-003 |
| `StepDefinition` | ✅ STABLE | PDR-001 | ISSUE-003 |
| `LoadModel` | ✅ STABLE | PDR-001 | ISSUE-003 |
| `RetryPolicy` | ✅ STABLE | PDR-001 | ISSUE-003 |
| `TaskResult` (String taskName) | ✅ STABLE | PDR-001 | ISSUE-004 |
| `ExecutionContext` | ✅ STABLE | PDR-001 | ISSUE-005 |
| `PartialExecutionContext` | ✅ STABLE | PDR-001 | ISSUE-005 |
| `ExecutionPlan` | ✅ STABLE | PDR-001 | ISSUE-006 |
| `ExecutionStep` | ✅ STABLE | PDR-001 | ISSUE-006 |
| `ExecutionState` | ✅ STABLE | PDR-001 | ISSUE-006 |
| `InjectionResult` | ✅ STABLE | PDR-001 | ISSUE-006 |
| `AssertionResult` | ✅ STABLE | PDR-001 | ISSUE-006 |
| `Evidence` | ✅ STABLE | PDR-001 | ISSUE-006 |
| `AgentDescriptor` | ⬜ PLANNED | PDR-001 | ISSUE-007 |
| `AgentCapabilities` | ⬜ PLANNED | PDR-001 | ISSUE-007 |
| `AgentHeartbeat` | ⬜ PLANNED | PDR-001 | ISSUE-007 |
| `TaskDefinition` | ❌ REMOVED | ADR-008 | — (remplacé par `StepDefinition`) |
| `TaskType` | ❌ REMOVED | — | — (remplacé par `String taskName`) |
| `AgentSelector` | ❌ REMOVED | ADR-008 | — |

### platform-domain.event

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `ScenarioStarted` / `ScenarioFinished` / `ScenarioCancelled` | ⬜ PLANNED | PDR-002 | ISSUE-008 |
| `PhaseStarted` / `PhaseCompleted` | ⬜ PLANNED | PDR-002 | ISSUE-008 |
| `TaskDispatched` / `TaskClaimedByAgent` / `TaskWorkInProgress` | ⬜ PLANNED | PDR-002 | ISSUE-008 |
| `TaskStarted` / `TaskCompleted` / `TaskFailed` / `TaskRetried` | ⬜ PLANNED | PDR-002 | ISSUE-008 |
| `AssertionPassed` / `AssertionFailed` | ⬜ PLANNED | PDR-002 | ISSUE-009 |
| `AgentRegistered` / `AgentLost` / `AgentRecovered` | ⬜ PLANNED | PDR-002 | ISSUE-009 |
| `ReportGenerated` / `ReportPublished` | ⬜ PLANNED | PDR-002 | ISSUE-009 |
| `AgentSignal` (sealed) | ⬜ PLANNED | PDR-002 | ISSUE-009 |
| `ScenarioRestartSignal` | ⬜ PLANNED | PDR-002 | ISSUE-009 |

## platform-plugin-api (module léger — 0 framework)

| Classe / Annotation | Statut | PDR | Issue |
|---|---|---|---|
| `@Preparation` ⚡ | ⬜ PLANNED | PDR-003 | ISSUE-010 |
| `@Injection` ⚡ | ⬜ PLANNED | PDR-003 | ISSUE-010 |
| `@Assertion` ⚡ | ⬜ PLANNED | PDR-003 | ISSUE-010 |
| `TaskExecutor` ⚡ | ⬜ PLANNED | PDR-003 | ISSUE-011 |
| `AssertionExecutor` | ⬜ PLANNED | PDR-003 | ISSUE-011 |

## platform-application (Ports)

| Interface | Statut | PDR | Issue |
|---|---|---|---|
| `ExecuteScenarioUseCase` | ⬜ PLANNED | PDR-004 | ISSUE-012 |
| `ScenarioParsingUseCase` | ⬜ PLANNED | PDR-004 | ISSUE-012 |
| `GetExecutionStatusUseCase` | ⬜ PLANNED | PDR-004 | ISSUE-012 |
| `CancelExecutionUseCase` | ⬜ PLANNED | PDR-004 | ISSUE-012 |
| `GenerateReportUseCase` | ⬜ PLANNED | PDR-004 | ISSUE-012 |
| `ExecutionRepository` | ⬜ PLANNED | PDR-004 | ISSUE-013 |
| `AgentRegistryPort` | ⬜ PLANNED | PDR-004 | ISSUE-013 |
| `ReportPublisherPort` | ⬜ PLANNED | PDR-004 | ISSUE-013 |
| `ExecutionConfig` | ⬜ PLANNED | PDR-004 | ISSUE-014 |

## platform-scenario-dsl

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `ScenarioParser` | ⬜ PLANNED | PDR-005 | ISSUE-015 |
| `ScenarioValidator` / `ValidationResult` | ⬜ PLANNED | PDR-005 | ISSUE-016 |
| `LoadModelRegistry` | ⬜ PLANNED | PDR-005 | ISSUE-017 |
| `DefaultScenarioParsingService` | ⬜ PLANNED | PDR-005 | ISSUE-018 |

## platform-execution-engine

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `ExecutionPlanBuilder` | ⬜ PLANNED | PDR-006 | ISSUE-019 |
| `RetryExecutor` | ⬜ PLANNED | PDR-006 | ISSUE-020 |
| `TaskCorrelationTracker` | ⬜ PLANNED | PDR-006 | ISSUE-021 |
| `AgentAvailabilityChecker` | ⬜ PLANNED | PDR-006 | ISSUE-022 |
| `ExecutionEngine` | ⬜ PLANNED | PDR-006 | ISSUE-023 |
| `LocalExecutionEngine` | ⬜ PLANNED | PDR-006 | ISSUE-023 |
| `RemoteExecutionEngine` | ⬜ PLANNED | PDR-006 | ISSUE-024 |

## platform-transport

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `ExecutionTransport` ⚡ | ⬜ PLANNED | PDR-007 | ISSUE-025 |
| `TaskRequestHandler` / `AgentSignalHandler` / `ExecutionEventHandler` / `Subscription` | ⬜ PLANNED | PDR-007 | ISSUE-025 |
| `TaskExecutionRequest` | ⬜ PLANNED | PDR-007 | ISSUE-026 |
| `ExecutionEvent` | ⬜ PLANNED | PDR-007 | ISSUE-026 |
| `InMemoryExecutionTransport` | ⬜ PLANNED | PDR-007 | ISSUE-027 |
| `TransportType` | ⬜ PLANNED | PDR-008 | ISSUE-028 |
| Transport properties + `TransportConfiguration` | ⬜ PLANNED | PDR-008 | ISSUE-028 |
| `KafkaExecutionTransport` | ⬜ PLANNED | PDR-008 | ISSUE-029 |
| `RabbitMQExecutionTransport` | ⬜ PLANNED | PDR-008 | ISSUE-030 |
| `HttpExecutionTransport` | ⬜ PLANNED | PDR-008 | ISSUE-031 |
| `SocketExecutionTransport` | ⬜ PLANNED | PDR-008 | ISSUE-032 |
| `TaskMessage` | ❌ REPLACED | ADR-008 | — (→ `TaskExecutionRequest`) |
| `GrpcExecutionTransport` | ❌ REMOVED | — | — (gRPC non implémenté) |

## platform-agent-runtime

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `TaskSpecializationFilter` / `TaskFilterResult` | ⬜ PLANNED | PDR-009 | ISSUE-033 |
| `AgentRegistrationPort` | ⬜ PLANNED | PDR-009 | ISSUE-034 |
| `AgentRegistry` | ⬜ PLANNED | PDR-009 | ISSUE-035 |
| `AgentRuntime` / `DistributedAgentRuntime` | ⬜ PLANNED | PDR-009 | ISSUE-036 |
| `StatefulResourceCleaner` / `ScenarioRestartHandler` | ⬜ PLANNED | PDR-009 | ISSUE-037 |
| `LocalAgent` | ⬜ PLANNED | PDR-009 | ISSUE-038 |
| `AgentAllocator` | ❌ REMOVED | ADR-008 | — |

## platform-infrastructure — `.executor` (PDR-010)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `TaskExecutorRegistry` | ⬜ PLANNED | PDR-010 | ISSUE-039 |
| `DatabaseTaskExecutor` | ⬜ PLANNED | PDR-010 | ISSUE-040 |
| `KafkaConsumerTaskExecutor` / `KafkaProducerTaskExecutor` | ⬜ PLANNED | PDR-010 | ISSUE-041 |
| `MockServerTaskExecutor` | ⬜ PLANNED | PDR-010 | ISSUE-042 |
| `ShellTaskExecutor` | ⬜ PLANNED | PDR-010 | ISSUE-043 |
| `DockerTaskExecutor` | ⬜ PLANNED | PDR-010 | ISSUE-044 |
| `FilesystemTaskExecutor` | ⬜ PLANNED | PDR-010 | ISSUE-045 |

## platform-infrastructure — `.plugin` (PDR-011)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `PluginLoader` / `PluginLoadResult` | ⬜ PLANNED | PDR-011 | ISSUE-046 |
| `PluginRegistry` | ⬜ PLANNED | PDR-011 | ISSUE-047 |
| `AnnotationScanner` / `PluginDescriptor` | ⬜ PLANNED | PDR-011 | ISSUE-048 |

## platform-infrastructure — `.persistence` (PDR-012)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| Entities JPA + Flyway | ⬜ PLANNED | PDR-012 | ISSUE-050 |
| `ExecutionStateMapper` / `TaskResultMapper` | ⬜ PLANNED | PDR-012 | ISSUE-051 |
| `JpaExecutionRepository` | ⬜ PLANNED | PDR-012 | ISSUE-052 |

## platform-infrastructure — `.publisher` (PDR-016)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `MultiPublisherDispatcher` | ⬜ PLANNED | PDR-016 | ISSUE-070 |
| `ConfluenceReportPublisher` | ⬜ PLANNED | PDR-016 | ISSUE-071 |
| `S3ReportPublisher` | ⬜ PLANNED | PDR-016 | ISSUE-072 |
| `GitReportPublisher` | ⬜ PLANNED | PDR-016 | ISSUE-073 |

## platform-injection-gatling

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `LoadModelTranslator` | ⬜ PLANNED | PDR-013 | ISSUE-054 |
| `GatlingRunner` / `GatlingRunConfig` | ⬜ PLANNED | PDR-013 | ISSUE-055 |
| `GatlingResultParser` | ⬜ PLANNED | PDR-013 | ISSUE-056 |
| `GatlingTaskExecutor` | ⬜ PLANNED | PDR-013 | ISSUE-057 |

## platform-assertion

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `AssertionExecutorRegistry` | ⬜ PLANNED | PDR-014 | ISSUE-059 |
| `GatlingMetricAssertionExecutor` | ⬜ PLANNED | PDR-014 | ISSUE-060 |
| `DatabaseAssertionExecutor` | ⬜ PLANNED | PDR-014 | ISSUE-061 |
| `KafkaAssertionExecutor` | ⬜ PLANNED | PDR-014 | ISSUE-062 |
| `HttpMockAssertionExecutor` | ⬜ PLANNED | PDR-014 | ISSUE-063 |
| `FileAssertionExecutor` | ⬜ PLANNED | PDR-014 | ISSUE-064 |

## platform-reporting

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `PublicationTarget` | ⬜ PLANNED | PDR-015 | ISSUE-065 |
| `ReportEngine` / `ReportRenderer` / `ReportPublisher` ⚡ / `CampaignReport` | ⬜ PLANNED | PDR-015 | ISSUE-065 |
| `DefaultReportEngine` / `VerdictCalculator` | ⬜ PLANNED | PDR-015 | ISSUE-066 |
| `HtmlReportRenderer` / `JsonReportRenderer` | ⬜ PLANNED | PDR-015 | ISSUE-067 |
| `PdfReportRenderer` | ⬜ PLANNED | PDR-015 | ISSUE-068 |
| `ReportFileWriter` | ⬜ PLANNED | PDR-015 | ISSUE-069 |

## platform-observability

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `ExecutionMetrics` / `MicrometerExecutionMetrics` | ⬜ PLANNED | PDR-017 | ISSUE-074 |
| `ObservabilityEventListener` | ⬜ PLANNED | PDR-017 | ISSUE-075 |
| `ObservabilityConfiguration` / `ExecutionContextMdcFilter` | ⬜ PLANNED | PDR-017 | ISSUE-076 |

## platform-app

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `PerformancePlatformApplication` | ⬜ PLANNED | PDR-018 | ISSUE-077 |
| `RuntimeModeResolver` / `RuntimeRole` | ⬜ PLANNED | PDR-018 | ISSUE-078 |
| `ScenarioController` | ⬜ PLANNED | PDR-018 | ISSUE-079 |
| `PluginBootstrap` | ⬜ PLANNED | PDR-018 | ISSUE-080 |
| `SecurityConfiguration` + config profiles | ⬜ PLANNED | PDR-018 | ISSUE-081 |

## platform-deployment

| Livrable | Statut | PDR | Issue |
|---|---|---|---|
| Dockerfile | ⬜ PLANNED | PDR-019 | ISSUE-083 |
| docker-compose | ⬜ PLANNED | PDR-019 | ISSUE-084 |
| Manifests K8s | ⬜ PLANNED | PDR-019 | ISSUE-085 |

---

> ⚡ = Interface publique critique — toute modification requiert un ADR.
> Les colonnes PDR et Issue sont remplies par le System Designer au moment de la création des PDRs.

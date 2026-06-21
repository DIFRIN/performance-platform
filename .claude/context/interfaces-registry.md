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
| `AgentDescriptor` | ✅ STABLE | PDR-001 | ISSUE-007 |
| `AgentCapabilities` | ✅ STABLE | PDR-001 | ISSUE-007 |
| `AgentHeartbeat` | ✅ STABLE | PDR-001 | ISSUE-007 |
| `TaskDefinition` | ❌ REMOVED | ADR-008 | — (remplacé par `StepDefinition`) |
| `TaskType` | ❌ REMOVED | — | — (remplacé par `String taskName`) |
| `AgentSelector` | ❌ REMOVED | ADR-008 | — |

### platform-domain.event

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `ScenarioStarted` / `ScenarioFinished` / `ScenarioCancelled` | ✅ STABLE | PDR-002 | ISSUE-008 |
| `PhaseStarted` / `PhaseCompleted` | ✅ STABLE | PDR-002 | ISSUE-008 |
| `TaskDispatched` / `TaskClaimedByAgent` / `TaskWorkInProgress` | ✅ STABLE | PDR-002 | ISSUE-008 |
| `TaskStarted` / `TaskCompleted` / `TaskFailed` / `TaskRetried` | ✅ STABLE | PDR-002 | ISSUE-008 |
| `AssertionPassed` / `AssertionFailed` | ✅ STABLE | PDR-002 | ISSUE-009 |
| `AgentRegistered` / `AgentLost` / `AgentRecovered` | ✅ STABLE | PDR-002 | ISSUE-009 |
| `ReportGenerated` / `ReportPublished` (String target) | ✅ STABLE | PDR-002 | ISSUE-009 |
| `AgentSignal` (sealed) | ✅ STABLE | PDR-002 | ISSUE-009 |
| `ScenarioRestartSignal` | ✅ STABLE | PDR-002 | ISSUE-009 |

## platform-plugin-api (module léger — 0 framework)

| Classe / Annotation | Statut | PDR | Issue |
|---|---|---|---|
| `@Preparation` ⚡ | ✅ STABLE | PDR-003 | ISSUE-010 |
| `@Injection` ⚡ | ✅ STABLE | PDR-003 | ISSUE-010 |
| `@Assertion` ⚡ | ✅ STABLE | PDR-003 | ISSUE-010 |
| `TaskExecutor` ⚡ | ✅ STABLE | PDR-003 | ISSUE-011 |
| `AssertionExecutor` | ✅ STABLE | PDR-003 | ISSUE-011 |
| `StatefulResourceCleaner` | 🔄 IN PROGRESS | PDR-009/PDR-010 | ISSUE-040 |

## platform-application (Ports)

| Interface | Statut | PDR | Issue |
|---|---|---|---|
| `ExecuteScenarioUseCase` | ✅ STABLE | PDR-004 | ISSUE-012 |
| `ScenarioParsingUseCase` | ✅ STABLE | PDR-004 | ISSUE-012 |
| `GetExecutionStatusUseCase` | ✅ STABLE | PDR-004 | ISSUE-012 |
| `CancelExecutionUseCase` | ✅ STABLE | PDR-004 | ISSUE-012 |
| `GenerateReportUseCase` | ✅ STABLE | PDR-004 | ISSUE-012 |
| `ExecutionRepository` | ✅ STABLE | PDR-004 | ISSUE-013 |
| `AgentRegistryPort` | ✅ STABLE | PDR-004 | ISSUE-013 |
| `ReportPublisherPort` | ✅ STABLE | PDR-004 | ISSUE-013 |
| `ExecutionConfig` | ✅ STABLE | PDR-004 | ISSUE-014 |

## platform-scenario-dsl

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `ScenarioParser` | ✅ STABLE | PDR-005 | ISSUE-015 |
| `ScenarioValidator` / `DefaultScenarioValidator` | ✅ STABLE | PDR-005 | ISSUE-016 |
| `ValidationResult` / `ValidationError` / `ValidationWarning` | ✅ STABLE | PDR-005 | ISSUE-016 |
| `DagCycleDetector` | ✅ STABLE | PDR-005 | ISSUE-016 |
| `LoadModelRegistry` / `DefaultLoadModelRegistry` / `LoadModelNotFoundException` | ✅ STABLE | PDR-005 | ISSUE-017 |
| `DefaultScenarioParsingService` | ✅ STABLE | PDR-005 | ISSUE-018 |
| `ScenarioValidationException` | ✅ STABLE | PDR-005 | ISSUE-018 |

## platform-execution-engine

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `ExecutionPlanBuilder` | ✅ STABLE | PDR-006 | ISSUE-019 |
| `DefaultExecutionPlanBuilder` | ✅ STABLE | PDR-006 | ISSUE-019 |
| `DagLevelCalculator` | ✅ STABLE | PDR-006 | ISSUE-019 |
| `RetryExecutor` / `DefaultRetryExecutor` | ✅ STABLE | PDR-006 | ISSUE-020 |
| `TaskCorrelationTracker` / `DefaultTaskCorrelationTracker` | ✅ STABLE | PDR-006 | ISSUE-021 |
| `AgentAvailabilityChecker` / `DefaultAgentAvailabilityChecker` | ✅ STABLE | PDR-006 | ISSUE-022 |
| `ExecutionEngine` | ✅ STABLE | PDR-006 | ISSUE-023 |
| `LocalExecutionEngine` | ✅ STABLE | PDR-006 | ISSUE-023 |
| `DagPhaseExecutor` | ✅ STABLE | PDR-006 | ISSUE-023 |
| `TaskExecutorLookup` | ✅ STABLE | PDR-006 | ISSUE-023 |
| `RemoteExecutionEngine` | ✅ STABLE | PDR-006 | ISSUE-024 |
| `PartialContextBuilder` | ✅ STABLE | PDR-006 | ISSUE-024 |

## platform-transport

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `ExecutionTransport` ⚡ | ✅ STABLE | PDR-007 | ISSUE-025 |
| `TaskRequestHandler` / `AgentSignalHandler` / `ExecutionEventHandler` / `Subscription` | ✅ STABLE | PDR-007 | ISSUE-025 |
| `TransportException` | ✅ STABLE | PDR-007 | ISSUE-025 |
| `TaskExecutionRequest` | ✅ STABLE | PDR-007 | ISSUE-026 |
| `ExecutionEvent` | ✅ STABLE | PDR-007 | ISSUE-026 |
| `TransportType` | ✅ STABLE | PDR-007 | ISSUE-025 |
| `InMemoryExecutionTransport` | ✅ STABLE | PDR-007 | ISSUE-027 |
| `AgentLifecycleEvent` | ✅ STABLE | PDR-007 | ADR-012 |
| `AgentLifecycleEventHandler` | ✅ STABLE | PDR-007 | ADR-012 |
| Transport properties + `TransportConfiguration` | ✅ STABLE | PDR-008 | ISSUE-028 |
| `KafkaTransportProperties` / `RabbitMQTransportProperties` / `HttpTransportProperties` / `SocketTransportProperties` | ✅ STABLE | PDR-008 | ISSUE-028 |
| `TransportConfiguration` | ✅ STABLE | PDR-008 | ISSUE-028 |
| `KafkaExecutionTransport` | ✅ STABLE | PDR-008 | ISSUE-029 |
| `KafkaMessageCodec` | ✅ STABLE | PDR-008 | ISSUE-029 |
| `KafkaConsumerManager` | ⚠️ BREAKING | PDR-021 | ISSUE-090 (→ DynamicKafkaListenerRegistry) |
| `KafkaSubscription` | ✅ STABLE | PDR-008 | ISSUE-029 |
| `RabbitMQExecutionTransport` | ✅ STABLE | PDR-008 | ISSUE-030 |
| `RabbitMQMessageCodec` | ✅ STABLE | PDR-008 | ISSUE-030 |
| `RabbitMQConsumerManager` | ✅ STABLE | PDR-008 | ISSUE-030 |
| `RabbitMQSubscription` | ✅ STABLE | PDR-008 | ISSUE-030 |
| `HttpExecutionTransport` | ✅ STABLE | PDR-008 | ISSUE-031 |
| `HttpEventCallbackController` | ✅ STABLE | PDR-008 | ISSUE-031 |
| `SocketExecutionTransport` | ✅ STABLE | PDR-008 | ISSUE-032 |
| `SocketConnectionRegistry` | ✅ STABLE | PDR-008 | ISSUE-032 |
| `TaskMessage` | ❌ REPLACED | ADR-008 | — (→ `TaskExecutionRequest`) |
| `GrpcExecutionTransport` | ❌ REMOVED | — | — (gRPC non implémenté) |

## platform-agent-runtime

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `TaskSpecializationFilter` / `TaskFilterResult` / `DefaultTaskSpecializationFilter` | ✅ STABLE | PDR-009 | ISSUE-033 |
| `AgentRegistrationPort` / `TransportAgentRegistration` / `HeartbeatScheduler` / `RegistrationException` | ✅ STABLE | PDR-009 | ISSUE-034 |
| `AgentRegistry` / `InMemoryAgentRegistry` / `AgentTtlMonitor` | ✅ STABLE | PDR-009 | ISSUE-035 |
| `AgentRuntime` / `DistributedAgentRuntime` | ✅ STABLE | PDR-009 | ISSUE-036 |
| `StatefulResourceCleaner` / `ScenarioRestartHandler` | ✅ STABLE | PDR-009 | ISSUE-037 |
| `LocalAgent` | ✅ STABLE | PDR-009 | ISSUE-038 |
| `AgentAllocator` | ❌ REMOVED | ADR-008 | — |

## platform-infrastructure — `.executor` (PDR-010)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `TaskExecutorRegistry` | ✅ STABLE | PDR-010 | ISSUE-039 |
| `DefaultTaskExecutorRegistry` | ✅ STABLE | PDR-010 | ISSUE-039 |
| `UnsupportedTaskNameException` | ✅ STABLE | PDR-010 | ISSUE-039 |
| `DatabaseTaskExecutor` | ✅ STABLE | PDR-010 | ISSUE-040 |
| `DatasourceProvider` | ✅ STABLE | PDR-010 | ISSUE-040 |
| `PlatformDatasourcesProperties` | ✅ STABLE | PDR-010 | ISSUE-040 (ADR-014) |
| `DatasourceConfiguration` | ✅ STABLE | PDR-010 | ISSUE-040 (ADR-014) |
| `KafkaConsumerTaskExecutor` / `KafkaProducerTaskExecutor` | ⚠️ BREAKING | PDR-020 | ISSUE-087,088 (refactoring Spring Kafka en cours) |
| `MockServerTaskExecutor` | ⚠️ BREAKING | PDR-022 | ISSUE-094 (refactoring target ref en cours) |
| `ShellTaskExecutor` | ✅ STABLE | PDR-010 | ISSUE-043 |
| `DockerTaskExecutor` | ✅ STABLE | PDR-010 | ISSUE-044 |
| `FilesystemTaskExecutor` | ✅ STABLE | PDR-010 | ISSUE-045 |

## platform-infrastructure — `.plugin` (PDR-011)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `PluginLoader` / `PluginLoadResult` | ✅ STABLE | PDR-011 | ISSUE-046 |
| `PluginRegistry` | ✅ STABLE | PDR-011 | ISSUE-047 |
| `AnnotationScanner` / `PluginDescriptor` | ✅ STABLE | PDR-011 | ISSUE-048 |
| `InfrastructurePackageSeparationTest` (ArchUnit) | ✅ STABLE | PDR-011 | ISSUE-049 |

## platform-infrastructure — `.persistence` (PDR-012)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `ExecutionStateEntity` / `TaskResultEntity` / `TaskResultId` | ✅ STABLE | PDR-012 | ISSUE-050 |
| `ExecutionStateMapper` / `TaskResultMapper` | ✅ STABLE | PDR-012 | ISSUE-051 |
| `ExecutionStateJpaRepository` | ✅ STABLE | PDR-012 | ISSUE-052 |
| `TaskResultJpaRepository` | ✅ STABLE | PDR-012 | ISSUE-052 |
| `JpaExecutionRepository` | ✅ STABLE | PDR-012 | ISSUE-052 |
| `PersistenceConfinementTest` (ArchUnit) | ✅ STABLE | PDR-012 | ISSUE-053 |

## platform-infrastructure — `.publisher` (PDR-016)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `MultiPublisherDispatcher` | ✅ STABLE | PDR-016 | ISSUE-070 |
| `PublishersProperties` | ✅ STABLE | PDR-016 | ISSUE-070 |
| `ConfluenceReportPublisher` | ✅ STABLE | PDR-016 | ISSUE-071 |
| `S3ReportPublisher` | ✅ STABLE | PDR-016 | ISSUE-072 |
| `GitReportPublisher` | ✅ STABLE | PDR-016 | ISSUE-073 |

## platform-injection-gatling

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `LoadModelTranslator` | ✅ STABLE | PDR-013 | ISSUE-054 |
| `DefaultLoadModelTranslator` | ✅ STABLE | PDR-013 | ISSUE-054 |
| `GatlingRunner` / `GatlingRunConfig` | ✅ STABLE | PDR-013 | ISSUE-055 |
| `DefaultGatlingRunner` / `GatlingExecutionException` / `SimulationInjectionHolder` | ✅ STABLE | PDR-013 | ISSUE-055 |
| `GatlingResultParser` / `DefaultGatlingResultParser` / `ResultParsingException` | ✅ STABLE | PDR-013 | ISSUE-056 |
| `GatlingTaskExecutor` | ✅ STABLE | PDR-013 | ISSUE-057 |
| `ProtocolSupportInfo` | ✅ STABLE | PDR-013 | ISSUE-058 |

## platform-assertion

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `AssertionExecutorRegistry` / `DefaultAssertionExecutorRegistry` / `UnsupportedAssertionNameException` | ✅ STABLE | PDR-014 | ISSUE-059 |
| `GatlingMetricAssertionExecutor` / `MetricExtractor` | ✅ STABLE | PDR-014 | ISSUE-060 |
| `DatabaseAssertionExecutor` | ✅ STABLE | PDR-014 | ISSUE-061 |
| `KafkaAssertionExecutor` | ✅ STABLE | PDR-014 | ISSUE-062 |
| `HttpMockAssertionExecutor` | ⚠️ BREAKING | PDR-022 | ISSUE-095 (refactoring target ref en cours) |
| `FileAssertionExecutor` | ✅ STABLE | PDR-014 | ISSUE-064 |

## platform-reporting

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `PublicationTarget` | ✅ STABLE | PDR-015 | ISSUE-065 |
| `ReportEngine` / `ReportRenderer` / `ReportPublisher` ⚡ / `CampaignReport` | ✅ STABLE | PDR-015 | ISSUE-065 |
| `EnvironmentInfo` / `ExecutionSummary` / `TaskReportEntry` | ✅ STABLE | PDR-015 | ISSUE-065 |
| `InjectionReportEntry` / `AssertionReportEntry` / `PublisherConfig` | ✅ STABLE | PDR-015 | ISSUE-065 |
| `RenderException` / `PublicationException` | ✅ STABLE | PDR-015 | ISSUE-065 |
| `DefaultReportEngine` / `VerdictCalculator` | ✅ STABLE | PDR-015 | ISSUE-066 |
| `HtmlReportRenderer` / `JsonReportRenderer` | ✅ STABLE | PDR-015 | ISSUE-067 |
| `PdfReportRenderer` | ✅ STABLE | PDR-015 | ISSUE-068 |
| `ReportFileWriter` | ✅ STABLE | PDR-015 | ISSUE-069 |
| `ReportProperties` | ✅ STABLE | PDR-015 | ISSUE-069 |

## platform-observability

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `ExecutionMetrics` / `MicrometerExecutionMetrics` | ✅ STABLE | PDR-017 | ISSUE-074 |
| `ObservabilityEventListener` | ✅ STABLE | PDR-017 | ISSUE-075 |
| `ObservabilityConfiguration` / `ExecutionContextMdcFilter` | ✅ STABLE | PDR-017 | ISSUE-076 |

## platform-app

| Interface / Classe | Statut | PDR | Issue |
|---|---|---|---|
| `PerformancePlatformApplication` | ✅ STABLE | PDR-018 | ISSUE-077 |
| `RuntimeModeResolver` / `RuntimeRole` / `RuntimeMode` | ✅ STABLE | PDR-018 | ISSUE-078 |
| `ScenarioController` | ✅ STABLE | PDR-018 | ISSUE-079 |
| `SubmitResponse` / `ExecutionStatusResponse` (DTOs) | ✅ STABLE | PDR-018 | ISSUE-079 |
| `ApiExceptionHandler` | ✅ STABLE | PDR-018 | ISSUE-079 |
| `PluginBootstrap` | ✅ STABLE | PDR-018 | ISSUE-080 |
| `PluginProperties` | ✅ STABLE | PDR-018 | ISSUE-080 |
| `SecurityConfiguration` + config profiles | ✅ STABLE | PDR-018 | ISSUE-081 |
| `application.yaml` (common config) | ✅ STABLE | PDR-018 | ISSUE-081 |
| `application-{local,orchestrator,agent}.yaml` | ✅ STABLE | PDR-018 | ISSUE-081 |
| `LocalFlowE2ETest` + `e2e-local.yaml` | ✅ STABLE | PDR-018 | ISSUE-082 |

## platform-infrastructure — `.executor.kafka` (PDR-020 — nouvelles classes)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `KafkaClusterProperties` | ✅ STABLE | PDR-020 | ISSUE-086 |
| `PlatformKafkaProperties` | ✅ STABLE | PDR-020 | ISSUE-086 |
| `KafkaClusterRegistry` | ✅ STABLE | PDR-020 | ISSUE-086 |
| `KafkaClusterConfiguration` | ✅ STABLE | PDR-020 | ISSUE-086 |

## platform-transport — nouvelles classes (PDR-021)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `DynamicKafkaListenerRegistry` | ⬜ PLANNED | PDR-021 | ISSUE-090 |
| `KafkaTransportBeans` | ✅ STABLE | PDR-021 | ISSUE-089 |

## platform-infrastructure — `.executor.http` (PDR-022 — nouvelles classes)

| Classe / Interface | Statut | PDR | Issue |
|---|---|---|---|
| `HttpTargetProperties` | 🔄 IN PROGRESS | PDR-022 | ISSUE-092 |
| `PlatformHttpTargetsProperties` | 🔄 IN PROGRESS | PDR-022 | ISSUE-092 |
| `HttpTargetRegistry` | 🔄 IN PROGRESS | PDR-022 | ISSUE-092 |
| `HttpTargetConfiguration` | 🔄 IN PROGRESS | PDR-022 | ISSUE-092 |
| `HttpClientTaskExecutor` | ⬜ PLANNED | PDR-022 | ISSUE-093 |

## platform-examples (PDR-023 — services SUT standalone)

| Classe / Service | Statut | PDR | Issue |
|---|---|---|---|
| `IotDispatcherApplication` + `DeviceCommandConsumer` + `DeviceRepository` + `IotHttpClient` | ⬜ PLANNED | PDR-023 | ISSUE-096 |
| `DeviceApiApplication` + `DeviceEventController` + `DeviceRepository` + `DeviceEventProducer` | ⬜ PLANNED | PDR-023 | ISSUE-097 |
| SQL schema `devices` + seed 10k | 🔄 IN PROGRESS | PDR-023 | ISSUE-098 |

## platform-deployment (PDR-024 — exemples et scénarios)

| Livrable | Statut | PDR | Issue |
|---|---|---|---|
| Dockerfile + .dockerignore | ✅ STABLE | PDR-019 | ISSUE-083 |
| docker-compose plateforme | ✅ STABLE | PDR-019 | ISSUE-084 |
| Manifests K8s | ✅ STABLE | PDR-019 | ISSUE-085 |
| `docker-compose-sut.yaml` (5 services SUT) | ⬜ PLANNED | PDR-024 | ISSUE-099 |
| `scenarios/iot-dispatcher-*.yaml` (LOCAL+DIST) | ⬜ PLANNED | PDR-024 | ISSUE-100 |
| `scenarios/device-api-*.yaml` (LOCAL+DIST) | ⬜ PLANNED | PDR-024 | ISSUE-101 |
| `README.md` examples guide | ⬜ PLANNED | PDR-024 | ISSUE-102 |

---

> ⚡ = Interface publique critique — toute modification requiert un ADR.
> Les colonnes PDR et Issue sont remplies par le System Designer au moment de la création des PDRs.
| ISSUE-088 | KafkaConsumerTaskExecutor | platform-infrastructure | @Preparation name=kafka-consumer v2.0.0, Consumer<String,String> via ConsumerFactory | IN PROGRESS |

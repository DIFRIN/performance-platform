# Progress

> NE PAS CHARGER EN CONTEXTE IA. Modifié uniquement par les scripts `.claude/scripts/issue-*.sh`.
> Pour voir létat : `bash .claude/scripts/progress-status.sh`

## Issues
| ID | Title | Status | PDR | Dependencies |
|----|-------|--------|-----|--------------|
| ISSUE-001 | Identifiants value objects | DONE | PDR-001 | - |
| ISSUE-002 | Enums du domaine | DONE | PDR-001 | - |
| ISSUE-003 | Records Scenario/Step/LoadModel/RetryPolicy | DONE | PDR-001 | ISSUE-001,002 |
| ISSUE-004 | TaskResult (String taskName) | DONE | PDR-001 | ISSUE-001,002 |
| ISSUE-005 | ExecutionContext + PartialExecutionContext | DONE | PDR-001 | ISSUE-001,004 |
| ISSUE-006 | ExecutionPlan/Step/State + VOs injection/assertion | DONE | PDR-001 | ISSUE-003,005 |
| ISSUE-007 | Records Agent + ArchUnit domaine | DONE | PDR-001 | ISSUE-001,002 |
| ISSUE-008 | Events cycle de vie scénario/phase/task | DONE | PDR-002 | ISSUE-001,002,004 |
| ISSUE-009 | Events agent/report + AgentSignal scellé | DONE | PDR-002 | ISSUE-001,007 |
| ISSUE-010 | Annotations @Preparation/@Injection/@Assertion | DONE | PDR-003 | ISSUE-003,004 |
| ISSUE-011 | Interfaces TaskExecutor/AssertionExecutor | DONE | PDR-003 | ISSUE-010,006 |
| ISSUE-012 | Ports entrants + exceptions applicatives | DONE | PDR-004 | ISSUE-003,006 |
| ISSUE-013 | Ports sortants (Repository/AgentRegistry/Publisher) | DONE | PDR-004 | ISSUE-012,007 |
| ISSUE-014 | ExecutionConfig | DONE | PDR-004 | ISSUE-012 |
| ISSUE-015 | ScenarioParser (YAML → ScenarioDefinition) | DONE | PDR-005 | ISSUE-003,012 |
| ISSUE-016 | ScenarioValidator + détection cycle DAG | DONE | PDR-005 | ISSUE-015 |
| ISSUE-019 | ExecutionPlanBuilder + DAG levels | DONE | PDR-006 | ISSUE-006,016 |
| ISSUE-020 | RetryExecutor (backoff exponentiel) | DONE | PDR-006 | ISSUE-019 |
| ISSUE-021 | TaskCorrelationTracker (multi-claim) | DONE | PDR-006 | ISSUE-019 |
| ISSUE-022 | AgentAvailabilityChecker | DONE | PDR-006 | ISSUE-019,013 |
| ISSUE-023 | LocalExecutionEngine | DONE | PDR-006 | ISSUE-019,020,013 |
| ISSUE-024 | RemoteExecutionEngine | DONE | PDR-006 | ISSUE-021,022,023,026 |
| ISSUE-025 | Interface ExecutionTransport + handlers | DONE | PDR-007 | ISSUE-009 |
| ISSUE-026 | TaskExecutionRequest + ExecutionEvent | DONE | PDR-007 | ISSUE-025 |
| ISSUE-027 | InMemoryExecutionTransport | DONE | PDR-007 | ISSUE-025,026 |
| ISSUE-033 | TaskSpecializationFilter + TaskFilterResult | DONE | PDR-009 | ISSUE-026,007 |
| ISSUE-034 | AgentRegistrationPort + heartbeat | DONE | PDR-009 | ISSUE-033 |
| ISSUE-035 | AgentRegistry (orchestrateur) | DONE | PDR-009 | ISSUE-034,013 |
| ISSUE-036 | DistributedAgentRuntime | DONE | PDR-009 | ISSUE-033,034 |
| ISSUE-037 | ScenarioRestart cleanup stateful | DONE | PDR-009 | ISSUE-036 |
| ISSUE-038 | LocalAgent | DONE | PDR-009 | ISSUE-036,027 |
| ISSUE-039 | TaskExecutorRegistry | DONE | PDR-010 | ISSUE-011 |
| ISSUE-040 | DatabaseTaskExecutor | DONE | PDR-010 | ISSUE-039 |
| ISSUE-041 | Kafka Consumer/Producer TaskExecutors | DONE | PDR-010 | ISSUE-039 |
| ISSUE-042 | MockServerTaskExecutor | DONE | PDR-010 | ISSUE-039 |
| ISSUE-050 | Entities JPA + migrations Flyway | DONE | PDR-012 | ISSUE-006,013 |
| ISSUE-051 | Mappers domain↔entity | DONE | PDR-012 | ISSUE-050 |
| ISSUE-052 | JpaExecutionRepository | DONE | PDR-012 | ISSUE-051,013 |
| ISSUE-054 | LoadModelTranslator (8 types) | DONE | PDR-013 | ISSUE-006,011 |
| ISSUE-055 | GatlingRunner | DONE | PDR-013 | ISSUE-054 |
| ISSUE-056 | GatlingResultParser | DONE | PDR-013 | ISSUE-055 |
| ISSUE-057 | GatlingTaskExecutor | DONE | PDR-013 | ISSUE-055,056 |
| ISSUE-059 | AssertionExecutorRegistry | DONE | PDR-014 | ISSUE-011 |
| ISSUE-060 | GatlingMetricAssertionExecutor | DONE | PDR-014 | ISSUE-059,056 |
| ISSUE-061 | DatabaseAssertionExecutor | DONE | PDR-014 | ISSUE-059 |
| ISSUE-065 | Records CampaignReport + interfaces | DONE | PDR-015 | ISSUE-006,013,002 |
| ISSUE-066 | DefaultReportEngine + Verdict | DONE | PDR-015 | ISSUE-065 |
| ISSUE-077 | SpringBoot main + Modulith + assemblage | DONE | PDR-018 | ISSUE-023,039,052 |
| ISSUE-078 | RuntimeModeResolver (env var prioritaire) | DONE | PDR-018 | ISSUE-077 |
| ISSUE-079 | API REST | DONE | PDR-018 | ISSUE-077,018 |
| ISSUE-086 | KafkaClusterRegistry + Properties + Configuration | DONE | PDR-020 | - |
| ISSUE-087 | Refactor KafkaProducerTaskExecutor → cluster ref + KafkaTemplate | DONE | PDR-020 | ISSUE-086 |
| ISSUE-088 | Refactor KafkaConsumerTaskExecutor → cluster ref + ConsumerFactory | DONE | PDR-020 | ISSUE-086 |
| ISSUE-089 | KafkaTemplate replace raw KafkaProducer dans KafkaExecutionTransport | DONE | PDR-021 | ISSUE-086 |
| ISSUE-090 | DynamicKafkaListenerRegistry replace KafkaConsumerManager | DONE | PDR-021 | ISSUE-089 |
| ISSUE-092 | HttpTargetRegistry + Properties + Configuration | DONE | PDR-022 | - |
| ISSUE-093 | Nouveau HttpClientTaskExecutor (@Preparation http-client) | DONE | PDR-022 | ISSUE-092 |
| ISSUE-096 | SUT iot-dispatcher Spring Boot (Kafka→DB→HTTP) | DONE | PDR-023 | ISSUE-098 |
| ISSUE-097 | SUT device-api Spring Boot (HTTP→DB→Kafka) | DONE | PDR-023 | ISSUE-098 |
| ISSUE-098 | SUT DB schema + seed 10k devices | DONE | PDR-023 | - |
| ISSUE-099 | docker-compose-sut.yaml (5 services SUT) | DONE | PDR-024 | ISSUE-096,097,098 |
| ISSUE-100 | Scénarios YAML iot-dispatcher (LOCAL + DISTRIBUTED) | DONE | PDR-024 | ISSUE-086,092,099 |
| ISSUE-101 | Scénarios YAML device-api (LOCAL + DISTRIBUTED) | DONE | PDR-024 | ISSUE-086,092,099 |
| ISSUE-017 | LoadModelRegistry | DONE | PDR-005 | ISSUE-015 |
| ISSUE-018 | ScenarioParsingUseCase | DONE | PDR-005 | ISSUE-015,016 |
| ISSUE-028 | Transport properties + Configuration | DONE | PDR-008 | ISSUE-025 |
| ISSUE-029 | KafkaExecutionTransport | DONE | PDR-008 | ISSUE-027,028 |
| ISSUE-030 | RabbitMQExecutionTransport | DONE | PDR-008 | ISSUE-027,028 |
| ISSUE-031 | HttpExecutionTransport | DONE | PDR-008 | ISSUE-027,028,013 |
| ISSUE-032 | SocketExecutionTransport | DONE | PDR-008 | ISSUE-027,028 |
| ISSUE-043 | ShellTaskExecutor | DONE | PDR-010 | ISSUE-039 |
| ISSUE-044 | DockerTaskExecutor | DONE | PDR-010 | ISSUE-039 |
| ISSUE-045 | FilesystemTaskExecutor | DONE | PDR-010 | ISSUE-039 |
| ISSUE-046 | PluginLoader | DONE | PDR-011 | ISSUE-011,039 |
| ISSUE-047 | PluginRegistry | DONE | PDR-011 | ISSUE-046 |
| ISSUE-048 | Scanner d'annotations plugin | DONE | PDR-011 | ISSUE-046 |
| ISSUE-049 | ArchUnit séparation packages infra | DONE | PDR-011 | ISSUE-039,046 |
| ISSUE-053 | ArchUnit JPA confiné | DONE | PDR-012 | ISSUE-052 |
| ISSUE-058 | Gatling Protocols (no gRPC) | DONE | PDR-013 | ISSUE-057 |
| ISSUE-062 | KafkaAssertionExecutor | DONE | PDR-014 | ISSUE-059 |
| ISSUE-063 | HttpMockAssertionExecutor | DONE | PDR-014 | ISSUE-059 |
| ISSUE-064 | FileAssertionExecutor | DONE | PDR-014 | ISSUE-059 |
| ISSUE-067 | HtmlReportRenderer + JsonReportRenderer | DONE | PDR-015 | ISSUE-065 |
| ISSUE-068 | PdfReportRenderer | DONE | PDR-015 | ISSUE-067 |
| ISSUE-069 | ReportFileWriter | DONE | PDR-015 | ISSUE-067,068 |
| ISSUE-070 | MultiPublisherDispatcher | DONE | PDR-016 | ISSUE-065,013 |
| ISSUE-071 | ConfluenceReportPublisher | DONE | PDR-016 | ISSUE-070 |
| ISSUE-072 | S3ReportPublisher | DONE | PDR-016 | ISSUE-070 |
| ISSUE-073 | GitReportPublisher | DONE | PDR-016 | ISSUE-070 |
| ISSUE-074 | ExecutionMetrics (Micrometer) | DONE | PDR-017 | ISSUE-001 |
| ISSUE-075 | ObservabilityEventListener | DONE | PDR-017 | ISSUE-074,008 |
| ISSUE-076 | Logging JSON + ObservabilityConfiguration | DONE | PDR-017 | ISSUE-074 |
| ISSUE-080 | PluginBootstrap | DONE | PDR-018 | ISSUE-077,047 |
| ISSUE-081 | Config local/orchestrator/agent + sécurité | DONE | PDR-018 | ISSUE-078 |
| ISSUE-082 | Test E2E mode LOCAL | DONE | PDR-018 | ISSUE-079,080,081,038,066 |
| ISSUE-083 | Dockerfile (<300MB) | DONE | PDR-019 | ISSUE-077 |
| ISSUE-084 | docker-compose dev local | DONE | PDR-019 | ISSUE-083 |
| ISSUE-085 | Manifests Kubernetes | DONE | PDR-019 | ISSUE-083 |
| ISSUE-091 | TransportConfiguration Spring Kafka autoconfiguration | DONE | PDR-021 | ISSUE-090 |
| ISSUE-094 | Refactor MockServerTaskExecutor → target reference | DONE | PDR-022 | ISSUE-092 |
| ISSUE-095 | Refactor HttpMockAssertionExecutor → target reference | DONE | PDR-022 | ISSUE-092 |
| ISSUE-102 | README examples + guide démarrage | DONE | PDR-024 | ISSUE-099,100,101 |
| ISSUE-103 (old, superseded) | Remove standalone WireMock from docker-compose-sut.yaml | DELETED | PDR-025-v1 | SUPERSEDED — see new ISSUE-103 below |
| ISSUE-104 (old, superseded) | Device example-only Javadoc markers | DELETED | PDR-025-v1 | SUPERSEDED — replaced by delete-executors ISSUE-104 below |
| ISSUE-105 (old, superseded) | http-api-mock-agent-local.yaml scenario | DELETED | PDR-025-v1 | SUPERSEDED — see new ISSUE-105 below |
| ISSUE-106 (old, superseded) | http-api-mock-agent-distributed.yaml scenario | DELETED | PDR-025-v1 | SUPERSEDED — see new ISSUE-106 below |
| ISSUE-107 (old, superseded) | docker-compose-wiremock-agent.yaml | DELETED | PDR-025-v1 | SUPERSEDED — see new ISSUE-107 below |
| ISSUE-108 (old, superseded) | README Mock-as-Agent + Device example-only | DELETED | PDR-025-v1 | SUPERSEDED — see new ISSUE-108 below |
| ISSUE-103 | Remove standalone WireMock from docker-compose-sut.yaml | DONE | PDR-025 | - |
| ISSUE-104 | Delete DevicePopulationTaskExecutor + DeviceCheckTaskExecutor from platform-infrastructure | DONE | PDR-025 | - |
| ISSUE-105 | Create http-api-mock-agent-local.yaml scenario (LOCAL mode) | DONE | PDR-025 | ISSUE-103 |
| ISSUE-106 | Create http-api-mock-agent-distributed.yaml scenario (DISTRIBUTED mode) | DONE | PDR-025 | ISSUE-105 |
| ISSUE-107 | Create docker-compose-wiremock-agent.yaml | DONE | PDR-025 | ISSUE-103 |
| ISSUE-108 | Update README with Mock-as-Agent architecture documentation | DONE | PDR-025 | ISSUE-103,104,105,106,107 |
| ISSUE-109 | Delete legacy device-check-perf.yaml scenario | DONE | PDR-025 | ISSUE-104 |
| ISSUE-110 | Clean device entries from interfaces-registry | DONE | PDR-025 | ISSUE-104,109 |
| ISSUE-111 | AgentProperties @ConfigurationProperties record | DONE | PDR-026 | — |
| ISSUE-112 | Create AgentRuntimeConfiguration @Configuration | DONE | PDR-026 | ISSUE-111 |
| ISSUE-113 | Wire LocalAgent with ALL task names from TaskExecutorRegistry | DONE | PDR-026 | ISSUE-112 |
| ISSUE-114 | Wire DistributedAgentRuntime with config-driven supportedTaskNames | DONE | PDR-026 | ISSUE-112 |
| ISSUE-115 | Add agent.supported-tasks to application-agent.yaml | DONE | PDR-026 | ISSUE-111 |
| ISSUE-116 | Replace AGENT_TAGS with AGENT_SUPPORTED_TASKS in ALL deployment files | DONE | PDR-026 | ISSUE-111 |
| ISSUE-117 | End-to-end integration test: agent config → registration → task execution | DONE | PDR-026 | ISSUE-111,112,113,114,115,116 |
| ISSUE-118 | End-to-end integration test: LOCAL mode executes all scenario tasks | DONE | PDR-026 | ISSUE-113 |
| ISSUE-119 | Etendre ExecutionRepository (findAll/deleteById) + JpaExecutionRepository | DONE | PDR-027 | — |
| ISSUE-120 | Use cases ListExecutions/DeleteExecution + ExecutionProgress serveur | DONE | PDR-027 | ISSUE-119 |
| ISSUE-121 | Endpoints REST executions (list / tasks / delete) + DTOs + progress | DONE | PDR-027 | ISSUE-120 |
| ISSUE-122 | Endpoint REST GET /api/v1/agents (ORCHESTRATOR) | DONE | PDR-027 | ISSUE-121 |
| ISSUE-123 | Endpoint REST GET /api/v1/executions/{id}/report (stream fichier deja genere) | DONE | PDR-027 | ISSUE-121 |
| ISSUE-124 | Endpoint POST /api/v1/scenarios/upload + DTO d'erreur de validation structure | DONE | PDR-027 | ISSUE-121 |
| ISSUE-125 | WebUiProperties + config Spring conditionnelle + securite static/UI | DONE | PDR-028 | ISSUE-124 |
| ISSUE-126 | Shell index.html + CSS layout + nav + routeur a hash | DONE | PDR-028 | ISSUE-125 |
| ISSUE-127 | Vue liste des executions (polling 3s, filtre statut, cancel/delete) | DONE | PDR-029 | ISSUE-126 |
| ISSUE-128 | Vue detail d'execution (tasks ok/ko, barre progression, phases) | DONE | PDR-029 | ISSUE-127 |
| ISSUE-129 | Vue dashboard agents (ORCHESTRATOR) + vue upload (validation inline) | APPROVED | PDR-029 | ISSUE-128 |
| ISSUE-130 | Report view (poll → iframe HTML + PDF/JSON download) + E2E Testcontainers | WAITING | PDR-029 | ISSUE-129 |
| ISSUE-131 | Headless CLI mode (run-and-exit on --scenario=, WebApplicationType.NONE) | WAITING | PDR-028 | ISSUE-125 |

## PDRs
| ID | Name | Module | Status | Issues | Deps |
|----|------|--------|--------|--------|------|
| PDR-001 | Domain Core Records | platform-domain | DONE | ISSUE-001..007 | — |
| PDR-002 | Domain Events | platform-domain | DONE | ISSUE-008,009 | PDR-001 |
| PDR-003 | Plugin API | platform-plugin-api | DONE | ISSUE-010,011 | PDR-001 |
| PDR-004 | Application Ports & Use Cases | platform-application | DONE | ISSUE-012,013,014 | PDR-001 |
| PDR-005 | Scenario DSL | platform-scenario-dsl | DONE | ISSUE-015..018 | PDR-001, PDR-004 |
| PDR-006 | Execution Engine | platform-execution-engine | DONE | ISSUE-019..024 | PDR-001,002,004,005,007 |
| PDR-007 | Transport Layer Core | platform-transport | DONE | ISSUE-025,026,027 | PDR-001, PDR-002 |
| PDR-008 | Transport Implementations | platform-transport | DONE | ISSUE-028..032 | PDR-001,002,007 |
| PDR-009 | Agent Runtime | platform-agent-runtime | DONE | ISSUE-033..038 | PDR-001,002,004,007 |
| PDR-010 | Task Executors (infra `.executor`) | platform-infrastructure | DONE | ISSUE-039..045 | PDR-001,003,004 |
| PDR-011 | Plugin System (infra `.plugin`) | platform-infrastructure | DONE | ISSUE-046..049 | PDR-001,003,010 |
| PDR-012 | Persistence (infra `.persistence`) | platform-infrastructure | DONE | ISSUE-050..053 | PDR-001,004 |
| PDR-013 | Gatling Injection | platform-injection-gatling | DONE | ISSUE-054..058 | PDR-001,003 |
| PDR-014 | Assertion Framework | platform-assertion | DONE | ISSUE-059..064 | PDR-001,003,013 |
| PDR-015 | Reporting Engine | platform-reporting | DONE | ISSUE-065..069 | PDR-001,002,004,013,014 |
| PDR-016 | Report Publishers (infra `.publisher`) | platform-infrastructure | DONE | ISSUE-070..073 | PDR-001,004,015 |
| PDR-017 | Observability | platform-observability | DONE | ISSUE-074,075,076 | PDR-001,002 |
| PDR-018 | Application Assembly | platform-app | DONE | ISSUE-077..082 | PDR-005,006,008,009,010,011,012,013,014,015,016,017 |
| PDR-019 | Deployment | platform-deployment | DONE | ISSUE-083,084,085 | PDR-018 |
| PDR-020 | Named Kafka Cluster Registry + Executors | platform-infrastructure | DONE | ISSUE-086,087,088 | PDR-010 |
| PDR-021 | Spring Kafka Migration — Transport | platform-transport | DONE | ISSUE-089,090,091 | PDR-020 |
| PDR-022 | HTTP Target Registry + HttpClientExecutor | platform-infrastructure | DONE | ISSUE-092,093,094,095 | PDR-010,PDR-020 |
| PDR-023 | SUT Example Services (IoT) | platform-examples/ | DONE | ISSUE-096,097,098 | — |
| PDR-024 | Scénarios IoT + Docker Compose SUT | platform-deployment | DONE | ISSUE-099,100,101,102 | PDR-020,PDR-022,PDR-023 |
| PDR-025 | Mock Agent Demo Scenarios + Device Cleanup | platform-deployment | WAITING (blocked by PDR-026 priority) | ISSUE-103,104,105,106,107,108,109,110 | PDR-010,013,014,018,019,022,023,024,026 |
| PDR-026 | Agent Configuration Wiring & E2E Verification | platform-app + platform-agent-runtime | WAITING | ISSUE-111,112,113,114,115,116,117,118 | PDR-009, PDR-018, ADR-015 |
| PDR-027 | IHM Backend API Extensions | platform-application + platform-infrastructure + platform-app | WAITING | ISSUE-119..124 | PDR-004,012,015,018 |
| PDR-028 | IHM Web Serving & Static Shell | platform-app | WAITING | ISSUE-125,126,131 | PDR-027 |
| PDR-029 | IHM Frontend Views (vanilla JS) | platform-app | WAITING | ISSUE-127..130 | PDR-028 |
| — | **NOTE: Configuration-driven model** | — | — | — | — |
| — | `agent.supported-tasks` config → `AgentDescriptor.supportedTaskNames` | PDR-009,PDR-018 | ⚠️ VERIFY | — | PDR-009 + PDR-018 must implement config-driven model, NOT auto-discovery from annotations |
| — | Annotations ONLY for PluginLoader (task-name → impl resolution) | PDR-003,PDR-011 | — | — | — |
| — | `agentTags` COMPLETELY REMOVED from scenarios — routing by `task:` name only | PDR-006,PDR-009 | — | — | — |
| — | LOCAL mode: specialization irrelevant, LocalAgent has all supportedTaskNames | PDR-009 | — | — | — |
| — | DISTRIBUTED mode: each agent has explicit `agent.supported-tasks` in config | PDR-009,PDR-018 | — | — | — |

## History
| Date | Issue | Transition | Note |
|------|-------|------------|------|
| 2026-06-12 | PDR-001..019 | — → TODO | System Designer |
| 2026-06-12 | ISSUE-001..085 | — → TODO | System Designer |
| 2026-06-12 | ISSUE-001 | TODO → IN PROGRESS | Developer |
| 2026-06-12 | ISSUE-001 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-12 | ISSUE-002 | TODO → IN PROGRESS | Developer |
| 2026-06-12 | ISSUE-001 | IN REVIEW → RE-REVIEW | Reviewer |
| 2026-06-12 | ISSUE-001 | RE-REVIEW → DONE | Reviewer |
| 2026-06-12 | ISSUE-002 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-12 | ISSUE-002 | IN REVIEW → DONE | Reviewer |
| 2026-06-13 | ISSUE-002 | DONE → CORRECTION | System Designer — TransportType et PublicationTarget retirés du domaine (fuite technologique) → déplacés ISSUE-028 et ISSUE-065 |
| 2026-06-13 | ISSUE-003 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-003 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-004 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-004 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-003 | IN REVIEW → DONE | Reviewer |
| 2026-06-13 | ISSUE-004 | IN REVIEW → DONE | Reviewer |
| 2026-06-13 | ISSUE-005 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-005 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-005 | IN REVIEW → DONE | Reviewer |
| 2026-06-13 | ISSUE-006 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-006 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-006 | IN REVIEW → DONE | Reviewer |
| 2026-06-13 | ISSUE-007 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-007 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-007 | IN REVIEW → DONE | Reviewer |
| 2026-06-13 | PDR-001 | TODO → DONE | Reviewer (ISSUE-001..007 all DONE) |
| 2026-06-13 | ISSUE-008 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-008 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-008 | IN REVIEW → DONE | Reviewer |
| 2026-06-13 | ISSUE-009 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-009 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-009 | IN REVIEW → DONE | Reviewer |
| 2026-06-13 | PDR-002 | IN PROGRESS → DONE | Reviewer (ISSUE-008,009 all DONE) |
| 2026-06-13 | ISSUE-010 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-010 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-011 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-011 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-011 | IN REVIEW → DONE | Reviewer |
| 2026-06-13 | PDR-003 | IN PROGRESS → DONE | Reviewer (ISSUE-010,011 all DONE) |
| 2026-06-13 | ISSUE-012 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-012 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-012 | IN REVIEW → DONE | Reviewer (rattrapage) |
| 2026-06-13 | ISSUE-013 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-013 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-013 | IN REVIEW → DONE | Reviewer |
| 2026-06-14 | ISSUE-014 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-014 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-014 | IN REVIEW → DONE | Reviewer |
| 2026-06-14 | PDR-004 | IN PROGRESS → DONE | Reviewer (ISSUE-012,013,014 all DONE) |
| 2026-06-14 | ISSUE-015 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | PDR-005 | TODO → IN PROGRESS | Developer (ISSUE-015) |
| 2026-06-14 | ISSUE-015 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-016 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-016 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-017 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-017 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-017 | IN REVIEW → DONE | Reviewer |
| 2026-06-14 | ISSUE-018 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-018 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-018 | IN REVIEW → DONE | Reviewer |
| 2026-06-14 | PDR-005 | IN PROGRESS → DONE | Reviewer (ISSUE-015..018 all DONE) |
| 2026-06-14 | ISSUE-019 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | PDR-006 | TODO → IN PROGRESS | Developer (ISSUE-019 started) |
| 2026-06-14 | ISSUE-019 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-019 | IN REVIEW → DONE | Reviewer |
| 2026-06-14 | ISSUE-020 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-020 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-020 | IN REVIEW → DONE | Reviewer (APPROVED, 0 bloquant) |
| 2026-06-14 | ISSUE-021 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-021 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-021 | IN REVIEW → DONE | Reviewer |
| 2026-06-14 | ISSUE-022 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-022 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-022 | IN REVIEW → DONE | Reviewer |
| 2026-06-14 | ISSUE-023 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-023 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-023 | IN REVIEW → DONE | Reviewer |
| 2026-06-14 | ISSUE-025 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | PDR-007 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-025 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-025 | IN REVIEW → DONE | Reviewer |
| 2026-06-14 | ISSUE-026 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-026 | IN PROGRESS → DONE | Developer |
| 2026-06-14 | ISSUE-026 | DONE → APPROVED | Reviewer |
| 2026-06-14 | ISSUE-024 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-024 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-024 | IN REVIEW → DONE | Reviewer |
| 2026-06-14 | PDR-006 | IN PROGRESS → DONE | Reviewer (ISSUE-019..024 all DONE) |
| 2026-06-14 | ISSUE-027 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-027 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-033 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-033 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-034 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-034 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-14 | ISSUE-034 | IN REVIEW → APPROVED | Reviewer |
| 2026-06-14 | ISSUE-035 | IN REVIEW → APPROVED | Reviewer |
| 2026-06-14 | ARCH-01..12 | APPLIED → CONFIRMED | Reviewer (re-review) |
| 2026-06-14 | ISSUE-027 | IN REVIEW → DONE | Reviewer (ARCH-06,11 confirmed) |
| 2026-06-14 | ISSUE-033 | IN REVIEW → DONE | Reviewer (re-review confirmed) |
| 2026-06-14 | ISSUE-034 | APPROVED → DONE | Reviewer (ARCH-01,03,05,06,07,09,12 confirmed) |
| 2026-06-14 | ISSUE-035 | APPROVED → DONE | Reviewer (ARCH-02,03,04,10 confirmed) |
| 2026-06-14 | PDR-007 | IN PROGRESS → DONE | Reviewer (ISSUE-025,026,027 all DONE) |
| 2026-06-15 | ISSUE-036 | TODO → IN PROGRESS | Developer |
| 2026-06-15 | ISSUE-036 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-15 | ISSUE-036 | IN REVIEW → APPROVED | Reviewer (0 bloquant, 5 recommandations PENDING) |
| 2026-06-15 | ISSUE-036 | APPROVED → DONE | Reviewer (re-review, 3 CONFIRMED, 2 DEFERRED) |
| 2026-06-15 | ISSUE-037 | TODO → IN PROGRESS | Developer |
| 2026-06-15 | ISSUE-037 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-15 | ISSUE-037 | IN REVIEW → DONE | Reviewer (APPROVED, 0 bloquant, 0 recommandation) |
| 2026-06-15 | ISSUE-038 | TODO → IN PROGRESS | Developer |
| 2026-06-15 | ISSUE-038 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-15 | ISSUE-038 | IN REVIEW → DONE | Reviewer (APPROVED, 0 bloquant, 0 recommandation) |
| 2026-06-15 | PDR-009 | IN PROGRESS → DONE | Reviewer (ISSUE-033..038 all DONE) |
| 2026-06-15 | ISSUE-037 | TODO → IN PROGRESS | Developer |
| 2026-06-15 | ISSUE-037 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-15 | ISSUE-038 | TODO → IN PROGRESS | Developer |
| 2026-06-15 | ISSUE-038 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-15 | ISSUE-039 | TODO → IN PROGRESS | Developer |
| 2026-06-15 | PDR-010 | TODO → IN PROGRESS | Developer (ISSUE-039 started) |
| 2026-06-15 | ISSUE-039 | IN REVIEW → CHANGES_REQUESTED | Reviewer |
| 2026-06-15 | ISSUE-039 | CHANGES_REQUESTED → DONE | Reviewer (re-review, 2 CONFIRMED) |
| 2026-06-15 | ISSUE-040 | TODO → IN PROGRESS | Developer |
| 2026-06-15 | ISSUE-040 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-15 | ISSUE-040 | ADR-013 + ADR-014 appliqués | Developer |
| 2026-06-15 | ISSUE-040 | IN REVIEW → DONE | Reviewer (APPROVED) |
| 2026-06-15 | ISSUE-041 | TODO → IN PROGRESS | Developer |
| 2026-06-15 | ISSUE-041 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-15 | ISSUE-041 | IN REVIEW → CHANGES_REQUESTED | Reviewer — CRAFT-05 violations CC-02 (6 methodes >40L, 1 classe >300L) |
| 2026-06-15 | ISSUE-041 | CHANGES_REQUESTED → DONE | Reviewer — re-review: 4 recommandations CONFIRMED (CRAFT-05/CRAFT-07/PRECISION/CRAFT-08) |
| 2026-06-15 | ISSUE-042 | TODO → IN PROGRESS | Developer |
| 2026-06-15 | ISSUE-042 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-15 | ISSUE-042 | IN REVIEW → APPROVED | Reviewer — 4 recommandations PENDING (CRAFT-05/CRAFT-08x2/CRAFT-07) |
| 2026-06-16 | ISSUE-042 | APPROVED → DONE | Reviewer — re-review: 4 recommandations CONFIRMED (CRAFT-05/CRAFT-08x2/CRAFT-07) |
| 2026-06-16 | ISSUE-043 | TODO → IN PROGRESS | Developer |
| 2026-06-16 | ISSUE-043 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-16 | ISSUE-043 | IN REVIEW → APPROVED | Reviewer — 2 recommandations PENDING (CRAFT-05 CC-02 + extraction, TEST-06 Thread.sleep) |
| 2026-06-16 | ISSUE-043 | APPROVED → DONE | Reviewer — re-review: 2 recommandations CONFIRMED (CRAFT-05/TEST-06) |
| 2026-06-16 | ISSUE-044 | TODO → IN PROGRESS | Developer |
| 2026-06-16 | ISSUE-044 | IN PROGRESS → IN REVIEW | Developer — DockerTaskExecutor + FakeDockerClient + 23 tests |
| 2026-06-18 | ISSUE-045 | TODO → IN PROGRESS | Developer |
| 2026-06-18 | ISSUE-045 | IN PROGRESS → IN REVIEW | Developer — FilesystemTaskExecutor + 20 tests @TempDir |
| 2026-06-19 | ISSUE-045 | IN REVIEW → CHANGES_REQUESTED | Reviewer — PRECISION-01 (pathsByExecution) + CRAFT-07 (logs) |
| 2026-06-19 | ISSUE-045 | CHANGES_REQUESTED → IN REVIEW | Developer — corrections appliquees (PRECISION-01/CRAFT-07), re-review |
| 2026-06-19 | ISSUE-045 | IN REVIEW → CHANGES_REQUESTED | Reviewer — 1 bloquant: pathsByExecution non alimenté + CRAFT-07 executionId absent des logs |
| 2026-06-16 | ISSUE-044 | IN REVIEW → DONE | Reviewer — APPROVED, 0 bloquant, 0 recommandation |
| 2026-06-19 | ISSUE-045 | IN REVIEW → DONE | Reviewer — re-review: PRECISION-01 + CRAFT-07 CONFIRMED, 0 bloquant |
| 2026-06-19 | ISSUE-046 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | PDR-011 | TODO → IN PROGRESS | Developer (ISSUE-046 started) |
| 2026-06-19 | ISSUE-046 | IN PROGRESS → IN REVIEW | Developer — PluginLoader + DefaultPluginLoader + 16 tests |
| 2026-06-19 | ISSUE-046 | IN REVIEW → APPROVED | Reviewer — APPROVED, 0 bloquant, 1 recommandation PENDING (CRAFT-05) |
| 2026-06-19 | ISSUE-046 | APPROVED (CRAFT-05 APPLIED) | Developer — CC-02 justification Javadoc sur load/loadExecutorsFromJar/tryLoadClass, re-review |
| 2026-06-19 | ISSUE-046 | APPROVED → DONE | Reviewer — re-review: CRAFT-05 CONFIRMED, 0 bloquant |
| 2026-06-19 | ISSUE-047 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | ISSUE-047 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-19 | ISSUE-047 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation CRAFT-05 PENDING (CC-02 constructeur) |
| 2026-06-19 | ISSUE-047 | APPROVED (CRAFT-05 APPLIED) | Developer — CC-02 justification Javadoc constructeur DefaultPluginRegistry, re-review |
| 2026-06-19 | ISSUE-047 | APPROVED → DONE | Reviewer — re-review: CRAFT-05 CONFIRMED, 0 bloquant |
| 2026-06-19 | ISSUE-048 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | ISSUE-048 | IN PROGRESS → IN REVIEW | Developer — AnnotationScanner + DefaultAnnotationScanner + PluginDescriptor + 14 tests |
| 2026-06-19 | ISSUE-048 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 15 tests OK, spec respectee, craft clean |
| 2026-06-19 | ISSUE-049 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | ISSUE-049 | IN PROGRESS → IN REVIEW | Developer — InfrastructurePackageSeparationTest + 14 regles ArchUnit + release 23 (ASM compat) |
| 2026-06-19 | ISSUE-049 | IN REVIEW → APPROVED | Reviewer — APPROVED: 0 bloquant, 1 recommandation PRECISION-02 PENDING (release 23 unnecessary) |
| 2026-06-19 | ISSUE-049 | APPROVED (PRECISION-02 APPLIED) | Developer — suppression <release>23</release> pom.xml, re-review |
| 2026-06-19 | ISSUE-049 | APPROVED → DONE | Reviewer — re-review: PRECISION-02 CONFIRMED, tous les tests OK |
| 2026-06-19 | PDR-011 | IN PROGRESS → DONE | Reviewer (ISSUE-046..049 all DONE) |
| 2026-06-19 | ISSUE-050 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | PDR-012 | TODO → IN PROGRESS | Developer (ISSUE-050 started) |
| 2026-06-19 | ISSUE-050 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-19 | ISSUE-050 | IN REVIEW → DONE | Reviewer (APPROVED, 0 bloquant, 0 recommandation) |
| 2026-06-19 | ISSUE-051 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | ISSUE-051 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-19 | ISSUE-051 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 32 tests OK |
| 2026-06-19 | ISSUE-052 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | ISSUE-052 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-19 | ISSUE-052 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation CRAFT-01 PENDING (Javadoc transactionalite) |
| 2026-06-19 | ISSUE-052 | APPROVED → DONE | Reviewer — re-review: CRAFT-01 CONFIRMED (Javadoc delegation transactionnelle corrigee) |
| 2026-06-19 | ISSUE-053 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | ISSUE-053 | IN PROGRESS → IN REVIEW | Developer — PersistenceConfinementTest (5 regles ArchUnit JPA confiné + @Entity leak) |
| 2026-06-19 | ISSUE-053 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 5 regles ArchUnit + 215 tests OK |
| 2026-06-19 | PDR-010 | IN PROGRESS → DONE | Reviewer — ISSUE-039..045 all DONE |
| 2026-06-19 | PDR-012 | IN PROGRESS → DONE | Reviewer — ISSUE-050..053 all DONE |
| 2026-06-19 | ISSUE-028 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | ISSUE-028 | IN PROGRESS → IN REVIEW | Developer — TransportType +CUSTOM, 4 properties records, TransportConfiguration avec @Bean conditionnels, 14 tests binding |
| 2026-06-19 | ISSUE-028 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 105 tests OK. |
| 2026-06-19 | ISSUE-029 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | ISSUE-029 | IN PROGRESS → IN REVIEW | Developer — KafkaExecutionTransport + KafkaMessageCodec + 13 ITs Testcontainers |
| 2026-06-19 | ISSUE-029 | IN REVIEW → APPROVED | Reviewer — APPROVED: 0 bloquant, 2 recommandations PENDING (CRAFT-08 @type constant, TEST-04 null-check tests) |
| 2026-06-19 | ISSUE-029 | APPROVED (CRAFT-08 + TEST-04 APPLIED) | Developer — TYPE_FIELD constante + 3 null-check tests, re-review |
| 2026-06-19 | ISSUE-029 | APPROVED → DONE | Reviewer — re-review #2: CRAFT-08 CONFIRMED, TEST-04 CONFIRMED (TransportException), 105 tests OK, commit |
| 2026-06-19 | ISSUE-030 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | ISSUE-030 | IN PROGRESS → IN REVIEW | Developer — RabbitMQExecutionTransport + MessageCodec + ConsumerManager + Subscription + 16 ITs |
| 2026-06-19 | ISSUE-031 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | ISSUE-031 | IN PROGRESS → IN REVIEW | Developer — HttpExecutionTransport + HttpEventCallbackController + 34 tests |
| 2026-06-19 | ISSUE-030 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 16 tests OK, CRAFT-08 @type constant appliqué, ack manuel correct |
| 2026-06-19 | ISSUE-031 | IN REVIEW → CHANGES_REQUESTED | Reviewer — CRAFT-05/CC-02: dispatchTask+broadcastSignal OK, classe 352L (>300) sans justification |
| 2026-06-19 | ISSUE-031 | CHANGES_REQUESTED → IN REVIEW | Developer — CC-02 Javadoc x3 (classe + dispatchTask + broadcastSignal) |
| 2026-06-19 | ISSUE-031 | IN REVIEW → DONE | Reviewer — re-review #2: APPROVED, CC-02 CONFIRMED x3, 139 tests OK |
| 2026-06-19 | ISSUE-032 | TODO → IN PROGRESS | Developer |
| 2026-06-19 | ISSUE-032 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-19 | ISSUE-032 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 2 recommandations PENDING (PRECISION-01/TEST-06) |
| 2026-06-19 | ISSUE-032 | APPROVED → DONE | Reviewer — re-review: PRECISION-01 + TEST-06 CONFIRMED, 0 bloquant, 33 tests OK |
| 2026-06-19 | PDR-008 | TODO → DONE | Reviewer (ISSUE-028..032 all DONE) |
| 2026-06-20 | ISSUE-055 | APPLIED → CONFIRMED | Reviewer — re-review: 4 recommandations CONFIRMED (CRAFT-05/SPEC-01/ROBUSTNESS-01/TEST-04). 28 tests OK. |
| 2026-06-20 | ISSUE-056 | IN PROGRESS → IN REVIEW | Developer — GatlingResultParser: correction p90Ms (interpolation), rawStats (types scalaires), 37 tests OK |
| 2026-06-20 | ISSUE-056 | IN REVIEW → APPROVED | Reviewer — APPROVED: 0 bloquant, 1 recommandation PENDING (CRAFT-05 CC-02 parse() 72L>40) |
| 2026-06-20 | ISSUE-056 | APPROVED → DONE | Reviewer — re-review: CRAFT-05 CONFIRMED (CC-02 Javadoc parse() ajoutee), 37 tests OK |
| 2026-06-20 | ISSUE-057 | TODO → IN REVIEW | Developer — GatlingTaskExecutor (@Injection name="gatling"), 55 tests OK |
| 2026-06-20 | ISSUE-057 | APPROVED → DONE | Reviewer — re-review: CRAFT-05 CONFIRMED (CC-02 Javadoc classe), 55 tests OK |
| 2026-06-20 | ISSUE-058 | TODO → IN REVIEW | Developer — ProtocolSupportInfo + pom.xml (HTTP/WS/Kafka/JMS, sans gRPC), 67 tests OK |
| 2026-06-20 | ISSUE-059 | TODO → IN REVIEW | Developer — platform-assertion module + AssertionExecutorRegistry, 10 tests OK |
| 2026-06-20 | ISSUE-058 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 67 tests OK, spec respectee |
| 2026-06-20 | ISSUE-054 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation: CRAFT-05 translateCustom() 41L marqueur CC-02 |
| 2026-06-20 | ISSUE-059 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation: SPEC-01 pom.xml injection-gatling deferred a ISSUE-060 |
| 2026-06-20 | ISSUE-054 | APPROVED → DONE | Reviewer — re-review: CRAFT-05 CC-02 translateCustom() CONFIRMED, 67 tests OK, PDR-013 DONE |
| 2026-06-20 | ISSUE-059 | APPROVED → DONE | Reviewer — re-review: SPEC-01 injection-gatling deferral CONFIRMED, PDR-014 IN PROGRESS |
| 2026-06-20 | PDR-013 | TODO → DONE | Reviewer (ISSUE-054..058 all DONE) |
| 2026-06-20 | PDR-014 | TODO → IN PROGRESS | Reviewer (ISSUE-059 DONE, ISSUE-060..064 remaining) |
| 2026-06-20 | ISSUE-060 | TODO → IN PROGRESS | Developer — GatlingMetricAssertionExecutor + MetricExtractor |
| 2026-06-20 | ISSUE-060 | IN PROGRESS → IN REVIEW | Developer — @Assertion name=gatling-metric, MetricExtractor 12 metriques, 27 tests OK |
| 2026-06-20 | ISSUE-060 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 2 recommandations PENDING (CRAFT-07 executionId logs, PRECISION import inutilise) |
| 2026-06-20 | ISSUE-060 | APPROVED — recommandations APPLIED | Developer — executionId ajoute aux 2 logs + import TaskStatus supprime. 37 tests OK |
| 2026-06-20 | ISSUE-060 | APPROVED → DONE | Reviewer — re-review: CRAFT-07 + PRECISION CONFIRMED, 37 tests OK |
| 2026-06-20 | ISSUE-061 | TODO → IN PROGRESS | Developer — DatabaseAssertionExecutor + ApplicationContext DataSource lookup |
| 2026-06-20 | ISSUE-062 | IN PROGRESS → IN REVIEW | Developer — @Assertion name=kafka, consumedCount/producedCount/lag, 63 tests OK |
| 2026-06-20 | ISSUE-062 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-061 | IN PROGRESS → IN REVIEW | Developer — @Assertion name=database, Virtual Threads JDBC, 17 ITs Testcontainers PostgreSQL OK |
| 2026-06-20 | ISSUE-061 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation PENDING [TEST-04] shouldErrorOnEmptyResult inefficace |
| 2026-06-20 | ISSUE-061 | APPROVED → DONE | Reviewer — re-review: [TEST-04] CONFIRMED, 54 tests OK (37 unit + 17 IT) |
| 2026-06-20 | ISSUE-062 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation PENDING [CRAFT-05] CC-02 method-level evaluate() |
| 2026-06-20 | ISSUE-062 | APPROVED → DONE | Reviewer — re-review: [CRAFT-05] CONFIRMED (CC-02 evaluate()), 63 tests OK |
| 2026-06-20 | ISSUE-064 | IN REVIEW → CHANGES_REQUESTED | Reviewer — CRAFT-07 BLOQUANT: 4 logs executionId incorrect (TaskId au lieu d'ExecutionId) |
| 2026-06-20 | ISSUE-065 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-066 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-066 | IN PROGRESS → IN REVIEW | Developer — DefaultReportEngine + VerdictCalculator, 24 tests OK (41 total) |
| 2026-06-20 | ISSUE-066 | IN REVIEW → RE-REVIEW | Developer — 3 recommandations CRAFT-05 APPLIED (CC-02 Javadoc classe + generate() + buildExecutionSummary()) |
| 2026-06-20 | ISSUE-067 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-067 | IN PROGRESS → IN REVIEW | Developer — HtmlReportRenderer + JsonReportRenderer + template HTML, 21 tests OK (62 total) |
| 2026-06-20 | ISSUE-065 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 17 tests OK, spec respectee, PublicationTarget dans platform-reporting (ARCH-11), records immuables copies defensives |
| 2026-06-20 | ISSUE-066 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 3 recommandations PENDING (CRAFT-05 CC-02 classe 329L + generate() 50L + buildExecutionSummary() 47L). 41 tests OK. |
| 2026-06-20 | ISSUE-066 | RE-REVIEW → DONE | Reviewer — 3 recommandations CONFIRMED (CC-02 Javadoc classe + generate() + buildExecutionSummary()). 41 tests OK. |
| 2026-06-20 | ISSUE-067 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 62 tests OK, spec respectee, craft clean. |
| 2026-06-20 | ISSUE-068 | TODO → IN PROGRESS | Developer
| 2026-06-20 | ISSUE-068 | IN PROGRESS → IN REVIEW | Developer — PdfReportRenderer + 11 tests, 73 total OK, %%EOF trailer validé
| 2026-06-20 | ISSUE-068 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation PENDING [TEST-04] shouldWrapConversionErrors trompeur (happy path, pas erreur). 73 tests OK. |
| 2026-06-20 | ISSUE-068 | APPROVED → DONE | Reviewer — re-review: [TEST-04] CONFIRMED (2 tests erreur avec stubs anonymes). 74 tests OK. |
| 2026-06-20 | ISSUE-069 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-069 | IN PROGRESS → IN REVIEW | Developer — ReportFileWriter + ReportProperties + 20 tests, 92 total OK |
| 2026-06-20 | ISSUE-069 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 2 recommandations PENDING (CRAFT-07 executionId logs, CRAFT-08 magic strings) |
| 2026-06-20 | ISSUE-069 | APPROVED → DONE | Reviewer — re-review: CRAFT-07 + CRAFT-08 CONFIRMED, 92 tests OK |
| 2026-06-20 | PDR-015 | IN PROGRESS → DONE | Reviewer (ISSUE-065..069 all DONE) |
| 2026-06-20 | PDR-015 | — → TESTS DONE | Tester — Integration tests: 62 new (contract/e2e/engine), 154 total. Contract: ReportRenderer (HTML/JSON/PDF), E2E: ReportingPipeline (14 scenarios), Integration: DefaultReportEngine (21 scenarios). BUILD SUCCESS. |
| 2026-06-20 | PDR-005,006,007,008,009,010,013,014 | — → E2E TESTS | Tester — E2E/Contract tests: 84 new across 6 modules (Scenario DSL: 24, Execution Engine: 14, Transport Contract: 21, Agent Runtime: 7, Gatling Pipeline: 9, Assertion Pipeline: 9). All pass. BUILD SUCCESS. |
| 2026-06-20 | ISSUE-070 | TODO → IN PROGRESS | Developer — MultiPublisherDispatcher + PublishersProperties |
| 2026-06-20 | ISSUE-070 | IN PROGRESS → IN REVIEW | Developer — MultiPublisherDispatcher + PublishersProperties + 11 tests |
| 2026-06-20 | ISSUE-070 | APPROVED (CONFIG-01 APPLIED) | Developer — Javadoc PublishersProperties prefixe platform.publishers vs reporting.* |
| 2026-06-20 | ISSUE-071 | TODO → IN PROGRESS | Developer — ConfluenceReportPublisher |
| 2026-06-20 | ISSUE-071 | IN PROGRESS → IN REVIEW | Developer — ConfluenceReportPublisher + 15 tests WireMock |
| 2026-06-20 | ISSUE-071 | CHANGES_REQUESTED → corrections APPLIED | Developer — CRAFT-05 CC-02 classe + publish() + CRAFT-08 KEY_* constantes |
| 2026-06-20 | ISSUE-070 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation PENDING (CONFIG-01 prefixe configuration). 11 tests OK, ArchUnit 17/17 OK. |
| 2026-06-20 | ISSUE-070 | APPROVED → DONE | Reviewer — re-review: CONFIG-01 CONFIRMED (Javadoc PublishersProperties prefixe platform.publishers vs reporting.*). 11 tests OK, ArchUnit 17/17 OK. |
| 2026-06-20 | ISSUE-071 | CHANGES_REQUESTED (re-review) → DONE | Reviewer — re-review: 3 recommandations CONFIRMED (CRAFT-05 CC-02 classe + CC-02 publish() + CRAFT-08 KEY_*). 15 tests OK, 241 total, BUILD SUCCESS. |
| 2026-06-20 | ISSUE-072 | TODO → IN PROGRESS | Developer — S3ReportPublisher |
| 2026-06-20 | ISSUE-072 | IN PROGRESS → IN REVIEW | Developer — S3ReportPublisher + 24 tests WireMock, 265 total OK, BUILD SUCCESS |
| 2026-06-20 | ISSUE-072 | IN REVIEW → CHANGES_REQUESTED | Reviewer — CRAFT-05: awsSign() 48L sans CC-02 method-level |
| 2026-06-20 | ISSUE-072 | CHANGES_REQUESTED → IN REVIEW (re-review) | Developer — 3 APPLIED: CRAFT-05 CC-02 awsSign() + SPEC-01 @Component + TEST-04 shouldThrowWhenAwsEnvVarsNotSet. 25 tests OK. |
| 2026-06-20 | ISSUE-072 | IN REVIEW (re-review) → DONE | Reviewer — 3 recommandations CONFIRMED (CRAFT-05/SPEC-01/TEST-04). 25 tests OK. Commit. |
| 2026-06-20 | ISSUE-073 | TODO → IN PROGRESS | Developer — GitReportPublisher |
| 2026-06-20 | ISSUE-073 | IN PROGRESS → IN REVIEW | Developer — GitReportPublisher + 20 tests, 286 total OK, BUILD SUCCESS |
| 2026-06-20 | ISSUE-073 | IN REVIEW → APPROVED | Reviewer — APPROVED: 0 bloquant, 1 recommandation [PRECISION] PENDING (logDir inutilisé runGit). 286 tests OK. |
| 2026-06-20 | ISSUE-073 | APPROVED → DONE | Reviewer — re-review: [PRECISION] CONFIRMED. Commit. |
| 2026-06-20 | PDR-016 | IN PROGRESS → DONE | Reviewer (ISSUE-070..073 all DONE) |
| 2026-06-20 | ISSUE-063 | IN REVIEW → CHANGES_REQUESTED | Reviewer — CRAFT-05: classe 379L >300 sans CC-02 classe (CC-02 sur evaluate() present mais manquant classe). 124 tests OK. |
| 2026-06-20 | ISSUE-063 | CHANGES_REQUESTED → DONE | Reviewer — re-review: CRAFT-05 CONFIRMED (CC-02 classe). 124 tests OK. Commit. |
| 2026-06-20 | PDR-014 | IN PROGRESS → DONE | Reviewer (ISSUE-059..064 all DONE) |
| 2026-06-20 | ISSUE-074 | TODO → IN PROGRESS | Developer — platform-observability: ExecutionMetrics + MicrometerExecutionMetrics + 23 tests |
| 2026-06-20 | ISSUE-074 | IN PROGRESS → IN REVIEW | Developer — 23 tests OK, 0 warning, BUILD SUCCESS |
| 2026-06-20 | PDR-017 | TODO → IN PROGRESS | Developer (ISSUE-074 started) |
| 2026-06-20 | ISSUE-074 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 23 tests OK. Commit. |
| 2026-06-20 | ISSUE-075 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-075 | IN PROGRESS → IN REVIEW | Developer — ObservabilityEventListener + 15 tests, 38 total OK, BUILD SUCCESS |
| 2026-06-20 | ISSUE-075 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 38 tests OK. |
| 2026-06-20 | ISSUE-076 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-076 | IN PROGRESS → IN REVIEW | Developer — ObservabilityConfiguration + ExecutionContextMdcFilter + logback-spring.xml + 19 tests, 57 total OK, BUILD SUCCESS |
| 2026-06-20 | ISSUE-076 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 57 tests OK. Commit. |
| 2026-06-20 | ISSUE-077 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-077 | IN PROGRESS → IN REVIEW | Developer — platform-app: pom.xml (11 modules) + PerformancePlatformApplication + 4 tests, fat JAR 131 MB, BUILD SUCCESS |
| 2026-06-20 | ISSUE-077 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 4 tests OK. Commit. |
| 2026-06-20 | PDR-017 | IN PROGRESS → DONE | Reviewer (ISSUE-074,075,076 all DONE) |
| 2026-06-20 | ISSUE-078 | IN REVIEW → RE-REVIEW | Developer — [TEST-04] APPLIED: System.getenv() → environment.getProperty() + 3 tests priorite. 27 tests OK. |
| 2026-06-20 | ISSUE-078 | RE-REVIEW → DONE | Reviewer — re-review: [TEST-04] CONFIRMED. 27 tests OK, BUILD SUCCESS. |
| 2026-06-20 | ISSUE-079 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-079 | IN PROGRESS → IN REVIEW | Developer — ScenarioController + 2 DTOs + ApiExceptionHandler + 11 tests. 43 total OK, BUILD SUCCESS |
| 2026-06-20 | ISSUE-079 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 3 recommandations PENDING (CRAFT-02/CRAFT-08/CRAFT-07) |
| 2026-06-20 | ISSUE-079 | APPROVED → DONE | Reviewer — re-review: 3 recommandations CONFIRMED (CRAFT-02/CRAFT-08/CRAFT-07) |
| 2026-06-20 | ISSUE-080 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-080 | IN PROGRESS → IN REVIEW | Developer — PluginBootstrap + PluginProperties + 8 tests, 51 total OK, BUILD SUCCESS |
| 2026-06-20 | ISSUE-080 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 2 recommandations PENDING (CRAFT-01/DOC + NAMING) |
| 2026-06-20 | ISSUE-080 | APPROVED → DONE | Reviewer — re-review: 2 recommandations CONFIRMED (CRAFT-01/DOC Javadoc true→false + NAMING shouldPropagateLoaderException). 51 tests OK. |
| 2026-06-20 | ISSUE-081 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-081 | IN PROGRESS → IN REVIEW | Developer — application.yaml, local/orchestrator/agent profiles, SecurityConfiguration OAuth2/JWT, ConfigProfilesTest (14 tests). 65 tests OK, BUILD SUCCESS. 0 warning. |
| 2026-06-20 | ISSUE-081 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 65 tests OK, BUILD SUCCESS. |
| 2026-06-20 | ISSUE-082 | WAITING → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-082 | IN PROGRESS → IN REVIEW | Developer — LocalFlowE2ETest + e2e-local.yaml + RawJpaExecutionRepository. 66 tests OK, BUILD SUCCESS. |
| 2026-06-20 | ISSUE-082 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation PENDING (VERSION: Testcontainers 1.20.4 → 1.20.6) |
| 2026-06-20 | ISSUE-082 | APPROVED → DONE | Reviewer — re-review: [VERSION] CONFIRMED (Testcontainers 1.20.6). 66 tests OK. Commit. |
| 2026-06-20 | PDR-018 | IN PROGRESS → DONE | Reviewer (ISSUE-077..082 all DONE) |
| 2026-06-20 | ISSUE-083 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | PDR-019 | TODO → IN PROGRESS | Developer (ISSUE-083 started) |
| 2026-06-20 | ISSUE-083 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-20 | ISSUE-083 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation PENDING ([PRECISION] exception .dockerignore inutile) |
| 2026-06-20 | ISSUE-083 | APPROVED → DONE | Reviewer — re-review: [PRECISION] CONFIRMED (suppression !platform-app/target/performance-platform.jar + commentaire chemin JAR) |
| 2026-06-20 | ISSUE-084 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-084 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 2 recommandations PENDING (SPEC-01 depends_on orchestrator, SPEC-02 AGENT_TAGS) |
| 2026-06-20 | ISSUE-084 | APPROVED → DONE | Reviewer — re-review: 2 recommandations CONFIRMED (SPEC-01 + SPEC-02). Commit. |
| 2026-06-20 | ISSUE-085 | TODO → IN PROGRESS | Developer |
| 2026-06-20 | ISSUE-085 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-20 | ISSUE-085 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation PENDING ([PRECISION] terminologie "headless" dans service.yaml) |
| 2026-06-20 | ISSUE-085 | APPROVED → DONE | Reviewer — re-review: [PRECISION] CONFIRMED (headless→external service placeholder), PDR-019 DONE |
| 2026-06-20 | PDR-019 | IN PROGRESS → DONE | Reviewer (ISSUE-083,084,085 all DONE) |
| 2026-06-21 | PDR-020..024 | — → WAITING | System Designer — 5 PDRs + 17 Issues (086..102) créés |
| 2026-06-21 | ISSUE-086..102 | — → WAITING | System Designer — Spring Kafka migration + Named registries + SUT IoT examples |
| 2026-06-21 | ISSUE-098 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. V1 + V2 SQL conformes PDR-023. |
| 2026-06-21 | ISSUE-086 | WAITING → IN PROGRESS | Developer |
| 2026-06-21 | ISSUE-086 | IN PROGRESS → IN REVIEW | Developer — KafkaClusterRegistry + KafkaClusterConfiguration + 17 tests |
| 2026-06-21 | ISSUE-092 | WAITING
| 2026-06-21 | ISSUE-092 | IN PROGRESS
| 2026-06-21 | ISSUE-098 | IN PROGRESS → IN REVIEW | Developer — V1__devices_schema.sql + V2__seed_10k_devices.sql |
| 2026-06-21 | ISSUE-087 | WAITING → IN PROGRESS → IN REVIEW | Developer — KafkaProducerTaskExecutor refactored to KafkaTemplate + KafkaClusterRegistry, 19 new unit tests |
| 2026-06-21 | ISSUE-087 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, code reviewed alongside ISSUE-088, 19 tests + IT OK |
| 2026-06-21 | ISSUE-088 | WAITING → IN PROGRESS → IN REVIEW | Developer — KafkaConsumerTaskExecutor refactored to Spring ConsumerFactory + KafkaClusterRegistry, 19 unit tests + IT updated, 355 total OK |
| 2026-06-21 | ISSUE-088 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 3 recommandations [PRECISION] PENDING, 355 tests OK |
| 2026-06-21 | ISSUE-088 | APPROVED → IN REVIEW | Developer — 3 recommandations APPLIED (PRECISION-01/02/03), re-review requested |
| 2026-06-21 | ISSUE-087 | APPROVED → DONE | Reviewer — re-review CONFIRMED (reviewed alongside ISSUE-088), 19 tests + IT OK |
| 2026-06-21 | ISSUE-088 | IN REVIEW → DONE | Reviewer — re-review: 3 PRECISION CONFIRMED, 0 bloquant, 355 tests OK. PDR-020 DONE. |
| 2026-06-21 | ISSUE-089 | WAITING → IN PROGRESS | Developer — Verification: code deja migre (KafkaTemplate remplace KafkaProducer). KafkaTransportBeans, TransportConfiguration, 20 tests existent. |
| 2026-06-21 | ISSUE-089 | IN PROGRESS → IN REVIEW | Developer — 213 tests OK, 0 raw KafkaProducer, KafkaTemplate.send() verifie. |
| 2026-06-21 | ISSUE-089 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 213 tests OK, KafkaTemplate confirmed. |
| 2026-06-21 | ISSUE-090 | WAITING → IN PROGRESS → IN REVIEW | Developer — DynamicKafkaListenerRegistry (331L) + 12 tests mock containers, KafkaExecutionTransport integre ConsumerFactory lazy init, KafkaConsumerManager @Deprecated, KafkaSubscription Runnable cleanup, KafkaTransportBeans ConsumerFactory bean. 225 tests OK, 0 warning. |
| 2026-06-22 | ISSUE-090 | CHANGES_REQUESTED → IN REVIEW | Developer (4 recommandations APPLIED: CRAFT-05 CC-02 x2 + CRAFT-08 ORCHESTRATOR_GROUP + DRY getOrCreateEventGroup) |
| 2026-06-22 | ISSUE-093 | IN PROGRESS → IN REVIEW | Developer — HttpClientTaskExecutor (189L) + 23 tests WireMock, BUILD SUCCESS |
| 2026-06-22 | ISSUE-093 | IN REVIEW → CHANGES_REQUESTED | Reviewer — CRAFT-05: execute() 47L + executeRequest() 57L sans CC-02. 39 tests OK. |
| 2026-06-22 | ISSUE-093 | CHANGES_REQUESTED → IN REVIEW | Developer (2 CRAFT-05 APPLIED: CC-02 execute() + executeRequest()) |
| 2026-06-22 | ISSUE-094 | WAITING → IN PROGRESS → IN REVIEW | Developer — MockServerTaskExecutor refactored with HttpTargetRegistry, 26 tests OK (23+3), BUILD SUCCESS |
| 2026-06-22 | ISSUE-096 | WAITING → IN PROGRESS | Developer |
| 2026-06-22 | ISSUE-101 | IN PROGRESS → IN REVIEW | Developer — device-api-local.yaml, device-api-distributed.yaml, application-examples-local.yaml (datasources.sut-db + http-targets.device-api), seed-sut-devices.sql. 4 parse tests OK, BUILD SUCCESS. |
| 2026-06-22 | ISSUE-101 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. 4 parse tests OK, BUILD SUCCESS, 188 total OK. |
| 2026-06-22 | ISSUE-100 | WAITING → IN PROGRESS | Developer — iot-dispatcher-local.yaml + distributed (files already exist, verified parseable) |
| 2026-06-22 | ISSUE-100 | IN PROGRESS → IN REVIEW | Developer — 2 scenarios YAML iot-dispatcher (LOCAL + DISTRIBUTED) + application-examples-local.yaml. 4 parse tests OK, 188 total OK, BUILD SUCCESS. 0 inline URL, agentTags OK. |
| 2026-06-22 | ISSUE-100 | IN REVIEW → APPROVED | Reviewer — 0 bloquant, 1 recommandation [PRECISION] PENDING (agentTags silencieusement ignores). 4 parse tests OK, BUILD SUCCESS. |
| 2026-06-22 | ISSUE-102 | TODO → IN PROGRESS → IN REVIEW | Developer — README.md examples + guide démarrage créé dans platform-deployment/examples/ (162 lignes). |
| 2026-06-22 | ISSUE-096 | IN PROGRESS → IN REVIEW | Developer — iot-dispatcher SUT: pom.xml + 4 classes + YAML + Dockerfile + 4 tests OK, BUILD SUCCESS |
| 2026-06-22 | ISSUE-097 | WAITING → IN REVIEW | Developer — device-api SUT: pom.xml + 4 classes + YAML + Dockerfile + 6 tests OK, BUILD SUCCESS |
| 2026-06-22 | ISSUE-099 | WAITING → IN REVIEW | Developer — docker-compose-sut.yaml + wiremock/mappings/iot-endpoints.json, YAML + JSON validated |
| 2026-06-22 | ISSUE-102 | IN REVIEW → DONE | Reviewer — APPROVED: 0 bloquant, 0 recommandation. README 162 lignes conforme spec, tous fichiers référencés existent. |
| 2026-06-22 | PDR-024 | IN PROGRESS → DONE | Reviewer — ISSUE-099,100,101,102 all DONE. |
| 2026-06-22 | ISSUE-095 | WAITING -> IN PROGRESS -> IN REVIEW | Developer — HttpMockAssertionExecutor refactored with HttpTargetRegistry (target param), RestClient v2 flow, legacy wiremockUrl + refTaskId support, 130 tests OK, BUILD SUCCESS. |
| 2026-06-22 | ISSUE-095 | IN REVIEW -> APPROVED | Reviewer -- 0 bloquant, 2 recommandations CRAFT-05 PENDING (CC-02 classe + evaluate() regressions ISSUE-063). 130 tests OK, BUILD SUCCESS. |
| 2026-06-22 | ISSUE-095 | APPROVED -> DONE | Reviewer — re-review: 2 CRAFT-05 CONFIRMED (CC-02 classe + evaluate() Javadoc). 130 tests OK, BUILD SUCCESS. PDR-022 DONE. |
| 2026-06-22 | PDR-025 + ISSUE-103..108 | — → WAITING | System Designer — Mock Agent Demo: WireMock as Agent (pas standalone), Device example-only, 6 Issues (v1). |
| 2026-06-22 | PDR-025 + ISSUE-103..110 | — → IN PROGRESS | System Designer — PDR-025 v2: DELETE Device executors (not mark), new scenarios LOCAL+DISTRIBUTED, docker-compose-wiremock-agent, README, delete device-check-perf.yaml, clean interfaces-registry. Old ISSUE-103..108 superseded. |
| 2026-06-22 | PDR-025 + ISSUE-103..108 | IN PROGRESS → UPDATED | System Designer — Configuration-driven model documented: `agent.supported-tasks` (NOT auto-discovery from annotations), `agentTags` COMPLETELY REMOVED from scenarios, routing by `task:` name only, LOCAL mode specialization irrelevant. PDR-009/PDR-018 flagged for follow-up verification. Annotations kept for PluginLoader only. |
| 2026-06-22 | ADR-015 | — → ACCEPTED | Architect — ADR-015 cree formalisant le modele configuration-driven. SUPERSEDES ADR-008. Code existant valide conforme (DefaultTaskSpecializationFilter, LocalAgent, DistributedAgentRuntime utilisent deja supportedTaskNames en parametre). Travail restant identifie: Spring @ConfigurationProperties pour agent.supported-tasks + mapping AGENT_SUPPORTED_TASKS dans PDR-009/PDR-018. |
| 2026-06-22 | PDR-026 + ISSUE-111..118 | — → WAITING | System Designer — PDR-026 Agent Configuration Wiring & E2E Verification cree (PRIORITY P0, ABOVE PDR-025). 8 Issues: AgentProperties @ConfigurationProperties, AgentRuntimeConfiguration @Configuration, LocalAgent + DistributedAgentRuntime wiring, YAML config, AGENT_TAGS→AGENT_SUPPORTED_TASKS migration, 2 tests E2E. PDR-025 marked WAITING (blocked by PDR-026 priority). |
| 2026-06-22 | ISSUE-111 | WAITING → IN PROGRESS | Developer — Implementation de AgentProperties @ConfigurationProperties record |
| 2026-06-22 | ISSUE-111 | IN PROGRESS → IN REVIEW | Developer — AgentProperties.java + AgentPropertiesTest.java (8 tests), @EnableConfigurationProperties dans PerformancePlatformApplication. 78 tests OK, 0 warning. |
| 2026-06-22 | ISSUE-111 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-111 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-112 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-112 | IN_REVIEW → CHANGES_REQUESTED | Issue status is IN_PROGRESS, not IN_REVIEW — run issue-finish.sh first to transition. The committed script changes (ff59663) are infrastructure improvements, not an Issue implementation under review. |
| 2026-06-22 | ISSUE-112 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-112 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-112 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-112 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-113 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-113 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-113 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-113 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-103 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-103 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-103 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-103 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-104 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-104 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-104 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-104 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-105 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-105 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-105 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-105 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-106 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-106 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-106 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-106 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-107 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-107 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-107 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-107 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-109 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-109 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-109 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-109 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-114 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-114 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-114 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-114 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-115 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-115 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-115 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-115 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-116 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-116 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-116 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-116 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-118 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-118 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-118 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-118 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-108 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-108 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-108 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-108 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-110 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-110 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-110 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-110 | APPROVED → DONE | issue-next.sh |
| 2026-06-22 | ISSUE-117 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-117 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-117 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-22 | ISSUE-117 | APPROVED → DONE | issue-next.sh |
| 2026-06-23 | ADR-021 + ISSUE-131 | — → WAITING | System Designer — Mode CLI headless (run-and-exit sur `--scenario=`). ADR-021 cree : detection `--scenario=` dans main(), `WebApplicationType.NONE` si present (aucun Tomcat/IHM), `CliScenarioRunner` (ApplicationRunner) parse→execute→poll→print stdout→exit (0/1/2). ISSUE-131 ajoutee a PDR-028 (depend de ISSUE-125). Le mode "existant PDR-018" suppose par PDR-028 n'existait pas dans le code ; il est desormais formalise (PDR-028 + ADR-021). 3e mode d'acces aux cotes API et IHM. |
| 2026-06-23 | PDR-027,028,029 + ISSUE-119..130 | — → WAITING | System Designer — Web IHM (HTML/CSS+vanilla JS dans platform-app, toggle web.ui.enabled, polling 3s, JWT property-driven). PDR-027 backend API extensions (findAll/deleteById, ListExecutions/DeleteExecution use cases, ExecutionProgress server-side, REST endpoints executions/tasks/agents/report-stream/upload + structured validation DTO). PDR-028 web serving + static shell (WebUiConfiguration, security, index.html/nav/hash router). PDR-029 frontend views (executions list, detail, agents, upload, report) + E2E Testcontainers. Report streamed already-generated, NEVER generated on demand. |
| 2026-06-23 | ISSUE-119 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-23 | ISSUE-119 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-23 | ISSUE-119 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-23 | ISSUE-119 | APPROVED → DONE | issue-next.sh |
| 2026-06-23 | ISSUE-120 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-23 | ISSUE-120 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-23 | ISSUE-120 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-23 | ISSUE-120 | APPROVED → DONE | issue-next.sh |
| 2026-06-23 | ISSUE-121 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-23 | ISSUE-121 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-23 | ISSUE-121 | APPROVED → DONE | issue-next.sh |
| 2026-06-23 | ISSUE-122 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-23 | ISSUE-122 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-23 | ISSUE-122 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-23 | ISSUE-122 | APPROVED → DONE | issue-next.sh |
| 2026-06-23 | ISSUE-123 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-23 | ISSUE-123 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-23 | ISSUE-123 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-23 | ISSUE-123 | APPROVED → DONE | issue-next.sh |
| 2026-06-23 | ISSUE-124 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-23 | ISSUE-124 | IN_PROGRESS → IN_REVIEW | issue-finish.sh (manual sed fix) |
| 2026-06-23 | ISSUE-124 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-23 | ISSUE-124 | APPROVED → DONE | issue-next.sh |
| 2026-06-23 | ISSUE-125 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-23 | ISSUE-125 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-23 | ISSUE-125 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-23 | ISSUE-125 | APPROVED → DONE | issue-next.sh |
| 2026-06-23 | ISSUE-126 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-23 | ISSUE-126 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-23 | ISSUE-126 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-23 | ISSUE-126 | APPROVED → DONE | issue-next.sh |
| 2026-06-23 | ISSUE-127 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-23 | ISSUE-127 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-23 | ISSUE-127 | IN_REVIEW → CHANGES_REQUESTED | BUG: api.js request() function cannot handle 202 Accepted with empty body. The POST /api/v1/executions/{id}/cancel endpoint returns ResponseEntity.accepted().build() (202 with no body). The request() function only special-cases status 204 for null return; for 202 it calls res.json() on an empty body, which throws SyntaxError. This causes the cancel action to appear to fail (alert) even though the server accepted it successfully. Fix: extend the empty-body check to cover 202 (or use res.text() and check for empty string before JSON.parse). |
| 2026-06-23 | ISSUE-127 | CHANGES_REQUESTED → IN_REVIEW | issue-finish.sh |
| 2026-06-23 | ISSUE-127 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-23 | ISSUE-127 | APPROVED → DONE | issue-next.sh |
| 2026-06-23 | ISSUE-128 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-23 | ISSUE-128 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-23 | ISSUE-128 | IN_REVIEW → APPROVED | Reviewer approved |
| 2026-06-23 | ISSUE-128 | APPROVED → DONE | issue-next.sh |
| 2026-06-23 | ISSUE-129 | WAITING → IN_PROGRESS | issue-start.sh |
| 2026-06-23 | ISSUE-129 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-23 | ISSUE-129 | IN_REVIEW → APPROVED | Reviewer approved |

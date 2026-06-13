# Architecture — Performance Engineering Platform

---

## 1. Vue d'Ensemble

```
┌─────────────────────────────────────────────────────────┐
│                   platform-app                          │
│              (Spring Boot Entry Point)                  │
│         Mode: LOCAL | ORCHESTRATOR | AGENT              │
└──────────────────────┬──────────────────────────────────┘
                       │
       ┌───────────────┼───────────────┐
       ▼               ▼               ▼
┌──────────┐   ┌──────────────┐  ┌──────────────┐
│ scenario │   │  execution   │  │    agent     │
│   dsl    │   │   engine     │  │   runtime    │
└──────────┘   └──────┬───────┘  └──────┬───────┘
                      │                  │
              ┌───────┴────────┐         │
              ▼                ▼         ▼
      ┌──────────────┐  ┌──────────────────────┐
      │  injection   │  │  transport layer     │
      │   gatling    │  │  (pluggable)         │
      └──────────────┘  └──────────────────────┘
              │
       ┌──────┴──────┐
       ▼             ▼
┌──────────┐  ┌──────────────┐
│assertion │  │   reporting  │
└──────────┘  └──────────────┘
       │
┌──────┴──────────────────────┐
│       platform-domain       │
│   (0 framework dependency)  │
└─────────────────────────────┘
```

---

## 2. Structure Maven Multi-Module

```
performance-platform/                   ← parent pom
│
├── platform-domain/                    ← Domaine pur (0 Spring, 0 framework)
│   └── src/main/java/com/performance/platform/domain/
│       ├── campaign/                   ← Campaign, ExecutionPlan, ExecutionContext
│       ├── scenario/                   ← ScenarioDefinition, StepDefinition
│       ├── task/                       ← TaskResult, TaskType, TaskId
│       ├── injection/                  ← InjectionResult, LoadModel, LoadModelType
│       ├── assertion/                  ← AssertionDefinition, AssertionResult, Evidence
│       ├── agent/                      ← AgentDescriptor, AgentId, AgentState
│       ├── report/                     ← CampaignReport, Verdict
│       └── event/                      ← Tous les DomainEvents
│
├── platform-application/               ← Use cases, Ports (interfaces)
│   └── src/main/java/com/performance/platform/application/
│       ├── ports/
│       │   ├── in/                     ← Driving ports (use case interfaces)
│       │   └── out/                    ← Driven ports (repo, transport, publisher)
│       ├── scenario/                   ← ScenarioParsingUseCase, ValidationUseCase
│       ├── execution/                  ← ExecuteScenarioUseCase, ExecuteTaskUseCase
│       ├── agent/                      ← RegisterAgentUseCase, AllocateAgentUseCase
│       └── report/                     ← GenerateReportUseCase, PublishReportUseCase
│
├── platform-infrastructure/            ← Adapters (implémentations des ports)
│   └── src/main/java/com/performance/platform/infrastructure/
│       ├── persistence/                ← JPA repositories, entities, mappers
│       ├── transport/                  ← Implémentations ExecutionTransport
│       │   ├── socket/
│       │   ├── rabbitmq/
│       │   ├── kafka/
│       │   └── grpc/
│       └── publisher/                  ← ReportPublisher implementations
│           ├── confluence/
│           ├── s3/
│           └── git/
│
├── platform-scenario-dsl/              ← YAML parsing, validation, LoadModel registry
│
├── platform-execution-engine/          ← DAG builder, phase orchestration, retry
│   └── LocalExecutionEngine
│   └── RemoteExecutionEngine
│
├── platform-agent-runtime/             ← Agent lifecycle, heartbeat, task receiver
│
├── platform-transport/                 ← ExecutionTransport interface + factory
│
├── platform-injection-gatling/         ← GatlingTaskExecutor, SimulationRunner
│
├── platform-assertion/                 ← AssertionExecutor implémentations
│
├── platform-reporting/                 ← ReportEngine, HTML/PDF/JSON generators
│
├── platform-observability/             ← Micrometer config, OTel config, metrics defs
│
├── platform-deployment/                ← Dockerfile, K8s manifests
├── platform-plugin-api/               ← Annotations @Preparation/@Injection/@Assertion + interfaces légères (0 framework)
│   ├── docker/
│   └── kubernetes/
│
└── platform-app/                       ← Main class, auto-configuration, assembly
    └── application-local.yaml
    └── application-orchestrator.yaml
    └── application-agent.yaml
```

---

## 3. Règles de Dépendance Entre Modules

```
platform-domain        ← aucune dépendance (cœur)
platform-application   ← dépend de platform-domain uniquement
platform-*             ← dépend de platform-application + platform-domain
platform-app           ← dépend de tous les modules
```

**Violations interdites** :
- `platform-domain` ne peut pas importer de Spring, Hibernate, Jackson
- `platform-application` ne peut pas importer d'adapteurs infrastructure
- Deux modules Spring Modulith ne peuvent pas s'appeler directement (via events uniquement)

---

## 4. Communication Interne (Event-Driven)

```
ExecutionEngine  ──publish──▶  ApplicationEventPublisher
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
             ReportingModule    ObservabilityModule   AgentModule
```

Events définis dans `platform-domain/event/` :
- `ScenarioStarted`, `ScenarioFinished`, `ScenarioCancelled`
- `TaskStarted`, `TaskCompleted`, `TaskFailed`, `TaskRetried`
- `AssertionPassed`, `AssertionFailed`
- `AgentRegistered`, `AgentLost`, `AgentRecovered`
- `ReportGenerated`, `ReportPublished`

---

## 5. Mode LOCAL vs DISTRIBUTED

### LOCAL
```
platform-app
└── LocalExecutionEngine
    ├── DAGExecutor (Virtual Threads)
    ├── TaskExecutorRegistry (local beans)
    └── GatlingTaskExecutor (in-process)
```

### DISTRIBUTED
```
Orchestrator JVM                        Agent JVM(s)
─────────────────────                   ──────────────
RemoteExecutionEngine                   AgentRuntime
├── AgentRegistry                       ├── TaskSpecializationFilter
├── AgentAvailabilityChecker            ├── TaskExecutorRegistry
├── TaskDispatcher                      ├── ExecutionTransport (receive)
├── TaskCorrelationTracker              └── EventPublisher
└── ExecutionTransport (broadcast) ─────────────────────────────▶
                       ◀─────────────── ExecutionEvent (claim/progress/result)
```

---

## 6. Extensibilité — Points d'Extension

| Point d'extension | Mécanisme | Exemple |
|---|---|---|
| Nouveau `TaskExecutor` préparation | `@Preparation(name="x")` + `getSupportedTaskName()` | `@Preparation(name="db-purge")` |
| Nouveau `TaskExecutor` injection | `@Injection(name="x")` + `getSupportedTaskName()` | `@Injection(name="k6-load")` |
| Nouveau `TaskExecutor` assertion | `@Assertion(name="x")` + `getSupportedTaskName()` | `@Assertion(name="prometheus-check")` |
| Plugin externe (JAR) | JAR dans `/plugins` + annotations ci-dessus | `acme-custom-tasks-1.0.jar` |
| Nouveau Transport | `@ConditionalOnProperty` ou `transport.custom-class` | `GrpcExecutionTransport` |
| Nouveau Publisher | `@ConditionalOnProperty` ou `reporting.publishers[].custom-class` | `NexusReportPublisher` |
| Nouveau LoadModel | Registre + `LoadModelBuilder` | `CustomLoadModelBuilder` |

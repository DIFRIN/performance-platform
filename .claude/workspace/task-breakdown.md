# Task Breakdown — RÉFÉRENCE SYSTEM DESIGNER UNIQUEMENT

> ⚠️ ARCHIVÉ après création des PDRs et Issues par le System Designer.
> Une fois `.claude/workspace/progress.md` initialisé, ce fichier ne doit PLUS être lu par le Developer.
> Source de vérité → `.claude/workspace/progress.md` + `.claude/workspace/pdr/*.md` + `.claude/issues/*.md`
>
> Usage unique : aide le System Designer à calibrer le découpage initial.
> En cas de conflit avec un PDR → le PDR fait autorité.

> Ce fichier est la RÉFÉRENCE INITIALE pour le System Designer.
> Il lui donne une base de découpage des phases en composants.
> Le System Designer produit ensuite les vrais PDRs et Issues dans `.claude/workspace/pdr/` et `.claude/issues/`.
>
> ⚠️ Ce fichier est un GUIDE, pas la source de vérité du plan.
> La source de vérité est `.claude/workspace/progress.md` + `.claude/workspace/pdr/*.md` + `.claude/issues/*.md`.
> Si une différence existe entre ce fichier et les PDRs créés par le System Designer,
> les PDRs font autorité.

---

## Phase 1 — Foundation (Domaine + DSL)

### Tâche 1.1 — Structure Maven + Records Domaine Core
**Durée estimée** : 1 session Developer  
**Périmètre** :
- `pom.xml` parent + tous les modules (structure uniquement, pas de code)
- Tous les records et enums de `platform-domain` liés à l'exécution :
  `ExecutionContext`, `ExecutionId`, `ExecutionPlan`, `ExecutionStep`,
  `ExecutionState`, `Phase`, `ScenarioId`, `TaskId`, `TaskType`, `TaskStatus`,
  `RetryPolicy`
- Tests unitaires pour `ExecutionContext` (immutabilité + copy-on-write)

**Specs à lire** : `02-execution-engine.md` sections 3+4, `.claude/knowledge/architecture.md` section 2  
**Skills à lire** : `hexagonal-architecture.md`  
**Output** : `platform-domain` compilable, 0 Spring, tous les records du glossaire

---

### Tâche 1.2 — Records Domaine : Scénario + Tâches + Résultats
**Durée estimée** : 1 session Developer  
**Périmètre** :
- `ScenarioDefinition`, `StepDefinition`, `LoadModel`, `LoadModelType`
- Note : `TaskDefinition`, `AgentSelector`, `TaskType` (enum) sont supprimés (ADR-008)
- `TaskResult`, `InjectionResult`, `AssertionResult`, `AssertionOperator`,
  `AssertionStatus`, `Evidence`, `Verdict`
- `AgentId`, `AgentDescriptor`, `AgentState`, `AgentCapabilities`
- Tests unitaires des factory methods de `TaskResult`

**Specs à lire** : `01-scenario-dsl.md` section 6, `03-task-framework.md` section 2  
**Prérequis** : Tâche 1.1 ✅

---

### Tâche 1.3 — Domain Events
**Durée estimée** : 0.5 session Developer (à fusionner avec 1.2 si rapide)  
**Périmètre** :
- Tous les records d'events dans `platform-domain/event/` :
  `ScenarioStarted`, `ScenarioFinished`, `ScenarioCancelled`,
  `PhaseStarted`, `PhaseCompleted`,
  `TaskStarted`, `TaskCompleted`, `TaskFailed`, `TaskRetried`,
  `AssertionPassed`, `AssertionFailed`,
  `AgentRegistered`, `AgentLost`, `AgentRecovered`,
  `ReportGenerated`, `ReportPublished`
- Pas de test unitaire nécessaire (records sans logique)

**Prérequis** : Tâche 1.1 + 1.2 ✅

---

### Tâche 1.4 — Scenario Parser YAML
**Durée estimée** : 1-2 sessions Developer  
**Périmètre** :
- `ScenarioParser` interface + `YamlScenarioParser` implémentation (SnakeYAML)
- `ScenarioValidator` interface + implémentation (toutes les règles spec-01 section 5)
- `LoadModelRegistry` interface + implémentation
- Exceptions : `ScenarioParsingException`, `ScenarioValidationException`, `ValidationResult`
- Tests unitaires parser + validator
- Fixtures YAML dans `src/test/resources/scenarios/` (7 fixtures listées dans spec Tester)

**Specs à lire** : `01-scenario-dsl.md` complet  
**Prérequis** : Tâches 1.1 + 1.2 ✅

---

## Phase 2 — Execution Engine LOCAL

### Tâche 2.1 — Ports Application + DAG Builder
**Durée estimée** : 1 session Developer  
**Périmètre** :
- Structure complète `platform-application` : tous les ports in/out (interfaces uniquement)
- `ExecutionPlanBuilder` interface + implémentation (BFS topologique sur dependsOn)
- Algorithme de détection de cycles (IllegalArgumentException si cycle)
- Tests unitaires : DAG simple, DAG complexe, cycle détecté

**Specs à lire** : `02-execution-engine.md` sections 2+3, `.claude/knowledge/architecture.md` section 3  
**Prérequis** : Phase 1 ✅

---

### Tâche 2.2 — LocalExecutionEngine + Retry
**Durée estimée** : 1-2 sessions Developer  
**Périmètre** :
- `ExecutionEngine` interface
- `LocalExecutionEngine` : orchestration des 3 phases, exécution DAG parallèle (Virtual Threads)
- `RetryExecutor` : retry avec backoff exponentiel
- Publication de tous les events (spec-02 section 8)
- Tests unitaires : phases en séquence, retry, skip sur dépendance échouée

**Specs à lire** : `02-execution-engine.md` complet  
**Skills** : `hexagonal-architecture.md`, `spring-modulith.md`  
**Prérequis** : Tâche 2.1 ✅

---

### Tâche 2.3 — Persistence + TaskExecutorRegistry
**Durée estimée** : 1 session Developer  
**Périmètre** :
- `ExecutionRepository` port + adapter JPA
- Entité JPA `ExecutionStateEntity` + mapper
- `TaskExecutorRegistry` : auto-découverte Spring beans
- Stub `NoOpTaskExecutor` pour tests d'intégration de l'engine

**Specs à lire** : `02-execution-engine.md` section 9, `.claude/knowledge/skills/hexagonal-architecture.md`  
**Prérequis** : Tâche 2.2 ✅

---

## Phase 3 — Task Executors

### Tâche 3.1 — DatabaseTaskExecutor + FilesystemTaskExecutor
**Durée estimée** : 1 session Developer + 1 session Tester (Testcontainers)  
**Périmètre** :
- `DatabaseTaskExecutor` (purge, populate, migration, backup, restore)
- `FilesystemTaskExecutor` (create, delete, upload, cleanup)
- Config datasources dans `application.yaml`

---

### Tâche 3.2 — ShellTaskExecutor + DockerTaskExecutor
**Durée estimée** : 1 session Developer  
**Périmètre** :
- `ShellTaskExecutor` avec capture stdout/stderr
- `DockerTaskExecutor` (start, stop, pull) via Docker Java API

---

### Tâche 3.3 — KafkaConsumerTaskExecutor + KafkaProducerTaskExecutor
**Durée estimée** : 1 session Developer + 1 session Tester (Testcontainers Kafka)

---

### Tâche 3.4 — MockServerTaskExecutor (WireMock embedded + external)
**Durée estimée** : 1 session Developer  
**Périmètre** :
- Embedded WireMock lifecycle (start/stop/reset/verify)
- Support external WireMock (URL externe, actions via API REST WireMock)

---

## Phase 4 — Gatling Integration

### Tâche 4.1 — LoadModelTranslator (tous les types)
**Durée estimée** : 1 session Developer  
**Périmètre** : `LoadModelTranslator` pour les 8 types de LoadModel → OpenInjectionStep

---

### Tâche 4.2 — GatlingRunner + GatlingResultParser
**Durée estimée** : 1-2 sessions Developer  
**Périmètre** :
- `GatlingRunner` : lancement Gatling in-process
- `GatlingResultParser` : parse stats.json → InjectionResult
- `GatlingTaskExecutor` : orchestration complète

---

## Phase 5 — Assertion Framework

### Tâche 5.1 — AssertionExecutorRegistry + GatlingMetricAssertionExecutor
**Durée estimée** : 1 session Developer

### Tâche 5.2 — Database + Kafka + Http + File AssertionExecutors
**Durée estimée** : 1 session Developer

---

## Phase 6 — Reporting

### Tâche 6.1 — ReportEngine + CampaignReport builder
**Durée estimée** : 1 session Developer

### Tâche 6.2 — HTML + PDF + JSON Renderers
**Durée estimée** : 1-2 sessions Developer

### Tâche 6.3 — Publishers (Confluence + S3 + Git)
**Durée estimée** : 1 session Developer par publisher

---

## Phase 7 — Transport + Distribution

### Tâche 7.1 — ExecutionTransport interface + KafkaExecutionTransport
**Durée estimée** : 1-2 sessions Developer + 1 session Tester (contrats)

### Tâche 7.2 — Socket + RabbitMQ + gRPC Transports
**Durée estimée** : 1 session Developer par transport

### Tâche 7.3 — AgentRuntime + Registration + Heartbeat
**Durée estimée** : 1-2 sessions Developer

### Tâche 7.4 — RemoteExecutionEngine + TaskDispatcher
**Durée estimée** : 1-2 sessions Developer

### Tâche 7.5 — Test E2E Distributed (docker-compose 2 agents)
**Durée estimée** : 1 session Tester

---

## Phase 8 — Observabilité + Sécurité

### Tâche 8.1 — Métriques Micrometer
### Tâche 8.2 — Traces OpenTelemetry
### Tâche 8.3 — TLS + OAuth2

---

## Phase 9 — Containerisation

### Tâche 9.1 — Dockerfile + docker-compose dev
### Tâche 9.2 — K8s manifests

---

## Phase 10 — Documentation

### Tâche 10.1 — Guides développeur (3 guides)
### Tâche 10.2 — Scénarios YAML d'exemple
### Tâche 10.3 — Diagrammes PlantUML

---

## Utilisation par le System Designer

Ce fichier sert de référence au System Designer pour :
1. Comprendre le découpage logique des phases
2. Calibrer la taille des Issues (une tâche ici ≈ une Issue M ou L)
3. Identifier les dépendances entre composants

Le System Designer NE copie PAS ces tâches telles quelles.
Il s'en inspire pour créer des PDRs et Issues avec les interfaces exactes.
Voir `.claude/agents/system-designer.md` pour le processus complet.

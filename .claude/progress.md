# Progress

> Tracker central de tous les PDRs et Issues du projet.
> **Source de vérité unique** pour l'avancement du développement.
>
> Lu EN PREMIER par le Developer à chaque nouvelle session.
> Mis à jour par : System Designer (création), Developer (statuts), Reviewer (validation DONE).
>
> Workflow de statut :
>   TODO → IN PROGRESS → IN REVIEW → DONE
>   TODO → BLOCKED (si dépendance non satisfaite)

---

## Démarrage de Session Developer — Protocole

```
1. Lire ce fichier (section PDRs + Issues uniquement — pas les descriptions)
2. Chercher dans cet ordre :
   a. Y a-t-il une Issue IN PROGRESS ?
      → Oui : reprendre cette issue (lire .claude/issues/ISSUE-XXX.md + session-state.md)
      → Non : continuer
   b. Y a-t-il une Issue TODO dont toutes les dépendances sont DONE ?
      → Oui : prendre la première P0, puis P1, puis P2
      → Non : continuer
   c. Toutes les Issues sont DONE ?
      → Informer l'humain : projet terminé ou nouvelles specs nécessaires
3. Marquer l'Issue choisie IN PROGRESS dans ce fichier
4. Lire .claude/issues/ISSUE-XXX.md pour le détail
```

---

## Vue d'Ensemble

> Pour un décompte rapide : `grep -c "TODO\|IN PROGRESS\|DONE" progress.md`

**Décisions de cadrage appliquées** :
- `TaskType` supprimé partout → `String taskName` (y compris dans `TaskResult`).
- gRPC NON implémenté → `TransportType` sans GRPC, pas de `GrpcExecutionTransport`.
- `platform-infrastructure` conservé, séparation stricte par packages :
  `.executor` (PDR-010), `.plugin` (PDR-011), `.persistence` (PDR-012), `.publisher` (PDR-016).

---

## PDRs

> Listés par ordre de construction. Un PDR ne peut démarrer que si ses dépendances sont DONE.

| ID | Nom | Module | Statut | Issues | Dépend de |
|---|---|---|---|---|---|
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
| PDR-021 | Spring Kafka Migration — Transport | platform-transport | WAITING | ISSUE-089,090,091 | PDR-020 |
| PDR-022 | HTTP Target Registry + HttpClientExecutor | platform-infrastructure | WAITING | ISSUE-092,093,094,095 | PDR-010,PDR-020 |
| PDR-023 | SUT Example Services (IoT) | platform-examples/ | WAITING | ISSUE-096,097,098 | — |
| PDR-024 | Scénarios IoT + Docker Compose SUT | platform-deployment | WAITING | ISSUE-099,100,101,102 | PDR-020,PDR-022,PDR-023 |

---

## Issues

> Listées par priorité puis par ordre de construction.
> Le Developer prend toujours la première IN PROGRESS, sinon la première TODO débloquée.

### 🔴 P0 — Bloquantes

| ID | Titre | PDR | Module | Taille | Statut | Bloquée par |
|---|---|---|---|---|---|---|
| ISSUE-001 | Identifiants value objects | PDR-001 | platform-domain | M | DONE | — |
| ISSUE-002 | Enums du domaine | PDR-001 | platform-domain | M | DONE | — |
| ISSUE-003 | Records Scenario/Step/LoadModel/RetryPolicy | PDR-001 | platform-domain | M | DONE | ISSUE-001,002 |
| ISSUE-004 | TaskResult (String taskName) | PDR-001 | platform-domain | S | DONE | ISSUE-001,002 |
| ISSUE-005 | ExecutionContext + PartialExecutionContext | PDR-001 | platform-domain | M | DONE | ISSUE-001,004 |
| ISSUE-006 | ExecutionPlan/Step/State + VOs injection/assertion | PDR-001 | platform-domain | M | DONE | ISSUE-003,005 |
| ISSUE-007 | Records Agent + ArchUnit domaine | PDR-001 | platform-domain | M | DONE | ISSUE-001,002 |
| ISSUE-008 | Events cycle de vie scénario/phase/task | PDR-002 | platform-domain | M | DONE | ISSUE-001,002,004 |
| ISSUE-009 | Events agent/report + AgentSignal scellé | PDR-002 | platform-domain | M | DONE | ISSUE-001,007 |
| ISSUE-010 | Annotations @Preparation/@Injection/@Assertion | PDR-003 | platform-plugin-api | S | DONE | ISSUE-003,004 |
| ISSUE-011 | Interfaces TaskExecutor/AssertionExecutor | PDR-003 | platform-plugin-api | S | DONE | ISSUE-010,006 |
| ISSUE-012 | Ports entrants + exceptions applicatives | PDR-004 | platform-application | M | DONE | ISSUE-003,006 |
| ISSUE-013 | Ports sortants (Repository/AgentRegistry/Publisher) | PDR-004 | platform-application | M | DONE | ISSUE-012,007 |

### 🟠 P1 — Critiques

| ID | Titre | PDR | Module | Taille | Statut | Bloquée par |
|---|---|---|---|---|---|---|
| ISSUE-014 | ExecutionConfig | PDR-004 | platform-application | S | DONE | ISSUE-012 |
| ISSUE-015 | ScenarioParser (YAML → ScenarioDefinition) | PDR-005 | platform-scenario-dsl | L | DONE | ISSUE-003,012 |
| ISSUE-016 | ScenarioValidator + détection cycle DAG | PDR-005 | platform-scenario-dsl | L | DONE | ISSUE-015 |
| ISSUE-019 | ExecutionPlanBuilder + DAG levels | PDR-006 | platform-execution-engine | L | DONE | ISSUE-006,016 |
| ISSUE-020 | RetryExecutor (backoff exponentiel) | PDR-006 | platform-execution-engine | M | DONE | ISSUE-019 |
| ISSUE-021 | TaskCorrelationTracker (multi-claim) | PDR-006 | platform-execution-engine | M | DONE | ISSUE-019 |
| ISSUE-022 | AgentAvailabilityChecker | PDR-006 | platform-execution-engine | M | DONE | ISSUE-019,013 |
| ISSUE-023 | LocalExecutionEngine | PDR-006 | platform-execution-engine | L | DONE | ISSUE-019,020,013 |
| ISSUE-024 | RemoteExecutionEngine | PDR-006 | platform-execution-engine | L | DONE | ISSUE-021,022,023,026 |
| ISSUE-025 | Interface ExecutionTransport + handlers | PDR-007 | platform-transport | M | DONE | ISSUE-009 |
| ISSUE-026 | TaskExecutionRequest + ExecutionEvent | PDR-007 | platform-transport | S | DONE | ISSUE-025 |
| ISSUE-027 | InMemoryExecutionTransport | PDR-007 | platform-transport | M | DONE | ISSUE-025,026 |
| ISSUE-033 | TaskSpecializationFilter + TaskFilterResult | PDR-009 | platform-agent-runtime | M | DONE | ISSUE-026,007 |
| ISSUE-034 | AgentRegistrationPort + heartbeat | PDR-009 | platform-agent-runtime | M | DONE | ISSUE-033 |
| ISSUE-035 | AgentRegistry (orchestrateur) | PDR-009 | platform-agent-runtime | M | DONE | ISSUE-034,013 |
| ISSUE-036 | DistributedAgentRuntime | PDR-009 | platform-agent-runtime | L | DONE | ISSUE-033,034 |
| ISSUE-037 | ScenarioRestart cleanup stateful | PDR-009 | platform-agent-runtime | M | DONE | ISSUE-036 |
| ISSUE-038 | LocalAgent | PDR-009 | platform-agent-runtime | M | DONE | ISSUE-036,027 |
| ISSUE-039 | TaskExecutorRegistry | PDR-010 | platform-infrastructure | M | DONE | ISSUE-011 |
| ISSUE-040 | DatabaseTaskExecutor | PDR-010 | platform-infrastructure | M | DONE | ISSUE-039 |
| ISSUE-041 | Kafka Consumer/Producer TaskExecutors | PDR-010 | platform-infrastructure | L | DONE | ISSUE-039 |
| ISSUE-042 | MockServerTaskExecutor | PDR-010 | platform-infrastructure | M | DONE | ISSUE-039 |
| ISSUE-050 | Entities JPA + migrations Flyway | PDR-012 | platform-infrastructure | M | DONE | ISSUE-006,013 |
| ISSUE-051 | Mappers domain↔entity | PDR-012 | platform-infrastructure | M | DONE | ISSUE-050 |
| ISSUE-052 | JpaExecutionRepository | PDR-012 | platform-infrastructure | M | DONE | ISSUE-051,013 |
| ISSUE-054 | LoadModelTranslator (8 types) | PDR-013 | platform-injection-gatling | L | DONE | ISSUE-006,011 |
| ISSUE-055 | GatlingRunner | PDR-013 | platform-injection-gatling | M | DONE | ISSUE-054 |
| ISSUE-056 | GatlingResultParser | PDR-013 | platform-injection-gatling | M | DONE | ISSUE-055 |
| ISSUE-057 | GatlingTaskExecutor | PDR-013 | platform-injection-gatling | M | DONE | ISSUE-055,056 |
| ISSUE-059 | AssertionExecutorRegistry | PDR-014 | platform-assertion | S | DONE | ISSUE-011 |
| ISSUE-060 | GatlingMetricAssertionExecutor | PDR-014 | platform-assertion | M | DONE | ISSUE-059,056 |
| ISSUE-061 | DatabaseAssertionExecutor | PDR-014 | platform-assertion | M | DONE | ISSUE-059 |
| ISSUE-065 | Records CampaignReport + interfaces | PDR-015 | platform-reporting | M | DONE | ISSUE-006,013,002 |
| ISSUE-066 | DefaultReportEngine + Verdict | PDR-015 | platform-reporting | M | DONE | ISSUE-065 |
| ISSUE-077 | SpringBoot main + Modulith + assemblage | PDR-018 | platform-app | M | DONE | ISSUE-023,039,052 |
| ISSUE-078 | RuntimeModeResolver (env var prioritaire) | PDR-018 | platform-app | M | DONE | ISSUE-077 |
| ISSUE-079 | API REST | PDR-018 | platform-app | M | DONE | ISSUE-077,018 |
| ISSUE-086 | KafkaClusterRegistry + Properties + Configuration | PDR-020 | platform-infrastructure | M | DONE | — |
| ISSUE-087 | Refactor KafkaProducerTaskExecutor → cluster ref + KafkaTemplate | PDR-020 | platform-infrastructure | M | DONE | ISSUE-086 |
| ISSUE-088 | Refactor KafkaConsumerTaskExecutor → cluster ref + ConsumerFactory | PDR-020 | platform-infrastructure | M | DONE | ISSUE-086 |
| ISSUE-089 | KafkaTemplate replace raw KafkaProducer dans KafkaExecutionTransport | PDR-021 | platform-transport | M | WAITING | ISSUE-086 |
| ISSUE-090 | DynamicKafkaListenerRegistry replace KafkaConsumerManager | PDR-021 | platform-transport | L | WAITING | ISSUE-089 |
| ISSUE-092 | HttpTargetRegistry + Properties + Configuration | PDR-022 | platform-infrastructure | M | DONE | — |
| ISSUE-093 | Nouveau HttpClientTaskExecutor (@Preparation http-client) | PDR-022 | platform-infrastructure | M | WAITING | ISSUE-092 |
| ISSUE-096 | SUT iot-dispatcher Spring Boot (Kafka→DB→HTTP) | PDR-023 | platform-examples/ | L | WAITING | ISSUE-098 |
| ISSUE-097 | SUT device-api Spring Boot (HTTP→DB→Kafka) | PDR-023 | platform-examples/ | M | WAITING | ISSUE-098 |
| ISSUE-098 | SUT DB schema + seed 10k devices | PDR-023 | platform-examples/ | S | DONE | — |
| ISSUE-099 | docker-compose-sut.yaml (5 services SUT) | PDR-024 | platform-deployment | S | WAITING | ISSUE-096,097,098 |
| ISSUE-100 | Scénarios YAML iot-dispatcher (LOCAL + DISTRIBUTED) | PDR-024 | platform-deployment | M | WAITING | ISSUE-086,092,099 |
| ISSUE-101 | Scénarios YAML device-api (LOCAL + DISTRIBUTED) | PDR-024 | platform-deployment | M | WAITING | ISSUE-086,092,099 |

### 🟡 P2 — Normales

| ID | Titre | PDR | Module | Taille | Statut | Bloquée par |
|---|---|---|---|---|---|---|
| ISSUE-017 | LoadModelRegistry | PDR-005 | platform-scenario-dsl | S | DONE | ISSUE-015 |
| ISSUE-018 | ScenarioParsingUseCase | PDR-005 | platform-scenario-dsl | S | DONE | ISSUE-015,016 |
| ISSUE-028 | Transport properties + Configuration | PDR-008 | platform-transport | M | DONE | ISSUE-025 |
| ISSUE-029 | KafkaExecutionTransport | PDR-008 | platform-transport | L | DONE | ISSUE-027,028 |
| ISSUE-030 | RabbitMQExecutionTransport | PDR-008 | platform-transport | L | DONE | ISSUE-027,028 |
| ISSUE-031 | HttpExecutionTransport | PDR-008 | platform-transport | L | DONE | ISSUE-027,028,013 |
| ISSUE-032 | SocketExecutionTransport | PDR-008 | platform-transport | L | DONE | ISSUE-027,028 |
| ISSUE-043 | ShellTaskExecutor | PDR-010 | platform-infrastructure | M | DONE | ISSUE-039 |
| ISSUE-044 | DockerTaskExecutor | PDR-010 | platform-infrastructure | M | DONE | ISSUE-039 |
| ISSUE-045 | FilesystemTaskExecutor | PDR-010 | platform-infrastructure | S | DONE | ISSUE-039 |
| ISSUE-046 | PluginLoader | PDR-011 | platform-infrastructure | L | DONE | ISSUE-011,039 |
| ISSUE-047 | PluginRegistry | PDR-011 | platform-infrastructure | M | DONE | ISSUE-046 |
| ISSUE-048 | Scanner d'annotations plugin | PDR-011 | platform-infrastructure | M | DONE | ISSUE-046 |
| ISSUE-049 | ArchUnit séparation packages infra | PDR-011 | platform-infrastructure | S | DONE | ISSUE-039,046 |
| ISSUE-053 | ArchUnit JPA confiné | PDR-012 | platform-infrastructure | S | DONE | ISSUE-052 |
| ISSUE-058 | Gatling Protocols (no gRPC) | PDR-013 | platform-injection-gatling | M | DONE | ISSUE-057 |
| ISSUE-062 | KafkaAssertionExecutor | PDR-014 | platform-assertion | M | DONE | ISSUE-059 |
| ISSUE-063 | HttpMockAssertionExecutor | PDR-014 | platform-assertion | M | DONE | ISSUE-059 |
| ISSUE-064 | FileAssertionExecutor | PDR-014 | platform-assertion | S | DONE | ISSUE-059 |
| ISSUE-067 | HtmlReportRenderer + JsonReportRenderer | PDR-015 | platform-reporting | M | DONE | ISSUE-065 |
| ISSUE-068 | PdfReportRenderer | PDR-015 | platform-reporting | S | DONE | ISSUE-067 |
| ISSUE-069 | ReportFileWriter | PDR-015 | platform-reporting | M | DONE | ISSUE-067,068 |
| ISSUE-070 | MultiPublisherDispatcher | PDR-016 | platform-infrastructure | M | DONE | ISSUE-065,013 |
| ISSUE-071 | ConfluenceReportPublisher | PDR-016 | platform-infrastructure | M | DONE | ISSUE-070 |
| ISSUE-072 | S3ReportPublisher | PDR-016 | platform-infrastructure | M | DONE | ISSUE-070 |
| ISSUE-073 | GitReportPublisher | PDR-016 | platform-infrastructure | M | DONE | ISSUE-070 |
| ISSUE-074 | ExecutionMetrics (Micrometer) | PDR-017 | platform-observability | M | DONE | ISSUE-001 |
| ISSUE-075 | ObservabilityEventListener | PDR-017 | platform-observability | M | DONE | ISSUE-074,008 |
| ISSUE-076 | Logging JSON + ObservabilityConfiguration | PDR-017 | platform-observability | S | DONE | ISSUE-074 |
| ISSUE-080 | PluginBootstrap | PDR-018 | platform-app | S | DONE | ISSUE-077,047 |
| ISSUE-081 | Config local/orchestrator/agent + sécurité | PDR-018 | platform-app | M | DONE | ISSUE-078 |
| ISSUE-082 | Test E2E mode LOCAL | PDR-018 | platform-app | L | DONE | ISSUE-079,080,081,038,066 |
| ISSUE-083 | Dockerfile (<300MB) | PDR-019 | platform-deployment | M | DONE | ISSUE-077 |
| ISSUE-084 | docker-compose dev local | PDR-019 | platform-deployment | S | DONE | ISSUE-083 |
| ISSUE-085 | Manifests Kubernetes | PDR-019 | platform-deployment | M | DONE | ISSUE-083 |
| ISSUE-091 | TransportConfiguration Spring Kafka autoconfiguration | PDR-021 | platform-transport | S | WAITING | ISSUE-090 |
| ISSUE-094 | Refactor MockServerTaskExecutor → target reference | PDR-022 | platform-infrastructure | S | WAITING | ISSUE-092 |
| ISSUE-095 | Refactor HttpMockAssertionExecutor → target reference | PDR-022 | platform-infrastructure | S | WAITING | ISSUE-092 |
| ISSUE-102 | README examples + guide démarrage | PDR-024 | platform-deployment | S | WAITING | ISSUE-099,100,101 |

---

## Historique des Changements de Statut

> Chaque changement de statut est loggé ici. Format : `[date] ISSUE-XXX : ANCIEN → NOUVEAU (agent)`

| Date | Item | Transition | Agent |
|---|---|---|---|
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

---

## Métriques

**Démarrage** : 2026-06-12
**PDRs totaux** : 24
**Issues totales** : 102
**Derniere mise a jour** : 2026-06-21 (Reviewer — ISSUE-087,088 DONE, PDR-020 DONE)

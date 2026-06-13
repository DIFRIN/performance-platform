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
| PDR-004 | Application Ports & Use Cases | platform-application | IN PROGRESS | ISSUE-012,013,014 | PDR-001 |
| PDR-005 | Scenario DSL | platform-scenario-dsl | TODO | ISSUE-015..018 | PDR-001, PDR-004 |
| PDR-006 | Execution Engine | platform-execution-engine | TODO | ISSUE-019..024 | PDR-001,002,004,005,007 |
| PDR-007 | Transport Layer Core | platform-transport | TODO | ISSUE-025,026,027 | PDR-001, PDR-002 |
| PDR-008 | Transport Implementations | platform-transport | TODO | ISSUE-028..032 | PDR-001,002,007 |
| PDR-009 | Agent Runtime | platform-agent-runtime | TODO | ISSUE-033..038 | PDR-001,002,004,007 |
| PDR-010 | Task Executors (infra `.executor`) | platform-infrastructure | TODO | ISSUE-039..045 | PDR-001,003,004 |
| PDR-011 | Plugin System (infra `.plugin`) | platform-infrastructure | TODO | ISSUE-046..049 | PDR-001,003,010 |
| PDR-012 | Persistence (infra `.persistence`) | platform-infrastructure | TODO | ISSUE-050..053 | PDR-001,004 |
| PDR-013 | Gatling Injection | platform-injection-gatling | TODO | ISSUE-054..058 | PDR-001,003 |
| PDR-014 | Assertion Framework | platform-assertion | TODO | ISSUE-059..064 | PDR-001,003,013 |
| PDR-015 | Reporting Engine | platform-reporting | TODO | ISSUE-065..069 | PDR-001,002,004,013,014 |
| PDR-016 | Report Publishers (infra `.publisher`) | platform-infrastructure | TODO | ISSUE-070..073 | PDR-001,004,015 |
| PDR-017 | Observability | platform-observability | TODO | ISSUE-074,075,076 | PDR-001,002 |
| PDR-018 | Application Assembly | platform-app | TODO | ISSUE-077..082 | PDR-005,006,008,009,010,011,012,013,014,015,016,017 |
| PDR-019 | Deployment | platform-deployment | TODO | ISSUE-083,084,085 | PDR-018 |

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
| ISSUE-015 | ScenarioParser (YAML → ScenarioDefinition) | PDR-005 | platform-scenario-dsl | L | TODO | ISSUE-003,012 |
| ISSUE-016 | ScenarioValidator + détection cycle DAG | PDR-005 | platform-scenario-dsl | L | TODO | ISSUE-015 |
| ISSUE-019 | ExecutionPlanBuilder + DAG levels | PDR-006 | platform-execution-engine | L | TODO | ISSUE-006,016 |
| ISSUE-020 | RetryExecutor (backoff exponentiel) | PDR-006 | platform-execution-engine | M | TODO | ISSUE-019 |
| ISSUE-021 | TaskCorrelationTracker (multi-claim) | PDR-006 | platform-execution-engine | M | TODO | ISSUE-019 |
| ISSUE-022 | AgentAvailabilityChecker | PDR-006 | platform-execution-engine | M | TODO | ISSUE-019,013 |
| ISSUE-023 | LocalExecutionEngine | PDR-006 | platform-execution-engine | L | TODO | ISSUE-019,020,013 |
| ISSUE-024 | RemoteExecutionEngine | PDR-006 | platform-execution-engine | L | TODO | ISSUE-021,022,023,026 |
| ISSUE-025 | Interface ExecutionTransport + handlers | PDR-007 | platform-transport | M | TODO | ISSUE-009 |
| ISSUE-026 | TaskExecutionRequest + ExecutionEvent | PDR-007 | platform-transport | S | TODO | ISSUE-025 |
| ISSUE-027 | InMemoryExecutionTransport | PDR-007 | platform-transport | M | TODO | ISSUE-025,026 |
| ISSUE-033 | TaskSpecializationFilter + TaskFilterResult | PDR-009 | platform-agent-runtime | M | TODO | ISSUE-026,007 |
| ISSUE-034 | AgentRegistrationPort + heartbeat | PDR-009 | platform-agent-runtime | M | TODO | ISSUE-033 |
| ISSUE-035 | AgentRegistry (orchestrateur) | PDR-009 | platform-agent-runtime | M | TODO | ISSUE-034,013 |
| ISSUE-036 | DistributedAgentRuntime | PDR-009 | platform-agent-runtime | L | TODO | ISSUE-033,034 |
| ISSUE-037 | ScenarioRestart cleanup stateful | PDR-009 | platform-agent-runtime | M | TODO | ISSUE-036 |
| ISSUE-038 | LocalAgent | PDR-009 | platform-agent-runtime | M | TODO | ISSUE-036,027 |
| ISSUE-039 | TaskExecutorRegistry | PDR-010 | platform-infrastructure | M | TODO | ISSUE-011 |
| ISSUE-040 | DatabaseTaskExecutor | PDR-010 | platform-infrastructure | M | TODO | ISSUE-039 |
| ISSUE-041 | Kafka Consumer/Producer TaskExecutors | PDR-010 | platform-infrastructure | L | TODO | ISSUE-039 |
| ISSUE-042 | MockServerTaskExecutor | PDR-010 | platform-infrastructure | M | TODO | ISSUE-039 |
| ISSUE-050 | Entities JPA + migrations Flyway | PDR-012 | platform-infrastructure | M | TODO | ISSUE-006,013 |
| ISSUE-051 | Mappers domain↔entity | PDR-012 | platform-infrastructure | M | TODO | ISSUE-050 |
| ISSUE-052 | JpaExecutionRepository | PDR-012 | platform-infrastructure | M | TODO | ISSUE-051,013 |
| ISSUE-054 | LoadModelTranslator (8 types) | PDR-013 | platform-injection-gatling | L | TODO | ISSUE-006,011 |
| ISSUE-055 | GatlingRunner | PDR-013 | platform-injection-gatling | M | TODO | ISSUE-054 |
| ISSUE-056 | GatlingResultParser | PDR-013 | platform-injection-gatling | M | TODO | ISSUE-055 |
| ISSUE-057 | GatlingTaskExecutor | PDR-013 | platform-injection-gatling | M | TODO | ISSUE-055,056 |
| ISSUE-059 | AssertionExecutorRegistry | PDR-014 | platform-assertion | S | TODO | ISSUE-011 |
| ISSUE-060 | GatlingMetricAssertionExecutor | PDR-014 | platform-assertion | M | TODO | ISSUE-059,056 |
| ISSUE-061 | DatabaseAssertionExecutor | PDR-014 | platform-assertion | M | TODO | ISSUE-059 |
| ISSUE-065 | Records CampaignReport + interfaces | PDR-015 | platform-reporting | M | TODO | ISSUE-006,013,002 |
| ISSUE-066 | DefaultReportEngine + Verdict | PDR-015 | platform-reporting | M | TODO | ISSUE-065 |
| ISSUE-077 | SpringBoot main + Modulith + assemblage | PDR-018 | platform-app | M | TODO | ISSUE-023,039,052 |
| ISSUE-078 | RuntimeModeResolver (env var prioritaire) | PDR-018 | platform-app | M | TODO | ISSUE-077 |
| ISSUE-079 | API REST | PDR-018 | platform-app | M | TODO | ISSUE-077,018 |

### 🟡 P2 — Normales

| ID | Titre | PDR | Module | Taille | Statut | Bloquée par |
|---|---|---|---|---|---|---|
| ISSUE-017 | LoadModelRegistry | PDR-005 | platform-scenario-dsl | S | TODO | ISSUE-015 |
| ISSUE-018 | ScenarioParsingUseCase | PDR-005 | platform-scenario-dsl | S | TODO | ISSUE-015,016 |
| ISSUE-028 | Transport properties + Configuration | PDR-008 | platform-transport | M | TODO | ISSUE-025 |
| ISSUE-029 | KafkaExecutionTransport | PDR-008 | platform-transport | L | TODO | ISSUE-027,028 |
| ISSUE-030 | RabbitMQExecutionTransport | PDR-008 | platform-transport | L | TODO | ISSUE-027,028 |
| ISSUE-031 | HttpExecutionTransport | PDR-008 | platform-transport | L | TODO | ISSUE-027,028,013 |
| ISSUE-032 | SocketExecutionTransport | PDR-008 | platform-transport | L | TODO | ISSUE-027,028 |
| ISSUE-043 | ShellTaskExecutor | PDR-010 | platform-infrastructure | M | TODO | ISSUE-039 |
| ISSUE-044 | DockerTaskExecutor | PDR-010 | platform-infrastructure | M | TODO | ISSUE-039 |
| ISSUE-045 | FilesystemTaskExecutor | PDR-010 | platform-infrastructure | S | TODO | ISSUE-039 |
| ISSUE-046 | PluginLoader | PDR-011 | platform-infrastructure | L | TODO | ISSUE-011,039 |
| ISSUE-047 | PluginRegistry | PDR-011 | platform-infrastructure | M | TODO | ISSUE-046 |
| ISSUE-048 | Scanner d'annotations plugin | PDR-011 | platform-infrastructure | M | TODO | ISSUE-046 |
| ISSUE-049 | ArchUnit séparation packages infra | PDR-011 | platform-infrastructure | S | TODO | ISSUE-039,046 |
| ISSUE-053 | ArchUnit JPA confiné | PDR-012 | platform-infrastructure | S | TODO | ISSUE-052 |
| ISSUE-058 | Protocoles Gatling (sans gRPC) | PDR-013 | platform-injection-gatling | M | TODO | ISSUE-057 |
| ISSUE-062 | KafkaAssertionExecutor | PDR-014 | platform-assertion | M | TODO | ISSUE-059 |
| ISSUE-063 | HttpMockAssertionExecutor | PDR-014 | platform-assertion | M | TODO | ISSUE-059 |
| ISSUE-064 | FileAssertionExecutor | PDR-014 | platform-assertion | S | TODO | ISSUE-059 |
| ISSUE-067 | HtmlReportRenderer + JsonReportRenderer | PDR-015 | platform-reporting | M | TODO | ISSUE-065 |
| ISSUE-068 | PdfReportRenderer | PDR-015 | platform-reporting | S | TODO | ISSUE-067 |
| ISSUE-069 | ReportFileWriter | PDR-015 | platform-reporting | M | TODO | ISSUE-067,068 |
| ISSUE-070 | MultiPublisherDispatcher | PDR-016 | platform-infrastructure | M | TODO | ISSUE-065,013 |
| ISSUE-071 | ConfluenceReportPublisher | PDR-016 | platform-infrastructure | M | TODO | ISSUE-070 |
| ISSUE-072 | S3ReportPublisher | PDR-016 | platform-infrastructure | M | TODO | ISSUE-070 |
| ISSUE-073 | GitReportPublisher | PDR-016 | platform-infrastructure | M | TODO | ISSUE-070 |
| ISSUE-074 | ExecutionMetrics (Micrometer) | PDR-017 | platform-observability | M | TODO | ISSUE-001 |
| ISSUE-075 | ObservabilityEventListener | PDR-017 | platform-observability | M | TODO | ISSUE-074,008 |
| ISSUE-076 | Logging JSON + ObservabilityConfiguration | PDR-017 | platform-observability | S | TODO | ISSUE-074 |
| ISSUE-080 | PluginBootstrap | PDR-018 | platform-app | S | TODO | ISSUE-077,047 |
| ISSUE-081 | Config local/orchestrator/agent + sécurité | PDR-018 | platform-app | M | TODO | ISSUE-078 |
| ISSUE-082 | Test E2E mode LOCAL | PDR-018 | platform-app | L | TODO | ISSUE-079,080,081,038,066 |
| ISSUE-083 | Dockerfile (<300MB) | PDR-019 | platform-deployment | M | TODO | ISSUE-077 |
| ISSUE-084 | docker-compose dev local | PDR-019 | platform-deployment | S | TODO | ISSUE-083 |
| ISSUE-085 | Manifests Kubernetes | PDR-019 | platform-deployment | M | TODO | ISSUE-083 |

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
| 2026-06-13 | ISSUE-013 | TODO → IN PROGRESS | Developer |
| 2026-06-13 | ISSUE-013 | IN PROGRESS → IN REVIEW | Developer |
| 2026-06-13 | ISSUE-013 | IN REVIEW → DONE | Reviewer |
| 2026-06-14 | ISSUE-014 | TODO → IN PROGRESS | Developer |
| 2026-06-14 | ISSUE-014 | IN PROGRESS → IN REVIEW | Developer |

---

## Métriques

**Démarrage** : 2026-06-12
**PDRs totaux** : 19
**Issues totales** : 85
**Dernière mise à jour** : 2026-06-14 (ISSUE-014 IN REVIEW)

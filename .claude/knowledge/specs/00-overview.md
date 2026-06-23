# Specifications Overview — Performance Engineering Platform

> Ce fichier est le point d'entrée des specs. Il décrit les relations entre elles
> et doit être lu avant toute spec individuelle.
> Pour comprendre QUI implémente quoi et dans quel ordre, voir `.claude/guides/agent-orchestration.md`.

---

## Index des Spécifications

| Fichier | Domaine | Module Maven Principal | Phase Roadmap |
|---|---|---|---|
| `01-scenario-dsl.md` | Parsing YAML, LoadModels | `platform-scenario-dsl` | Phase 1 |
| `02-execution-engine.md` | Orchestration, DAG, phases, retry | `platform-execution-engine` | Phase 2 |
| `03-task-framework.md` | TaskExecutor interface + tous les types | `platform-infrastructure` | Phase 3 |
| `04-agent-runtime.md` | Agent lifecycle, heartbeat, registration | `platform-agent-runtime` | Phase 7 |
| `05-transport-layer.md` | ExecutionTransport + 4 implémentations | `platform-transport` | Phase 7 |
| `06-injection-gatling.md` | Gatling integration, load models | `platform-injection-gatling` | Phase 4 |
| `07-assertion-framework.md` | Assertions post-injection, evidence | `platform-assertion` | Phase 5 |
| `08-report-engine.md` | HTML/PDF/JSON report, publishers | `platform-reporting` | Phase 6 |
| `09-deployment.md` | Docker, Kubernetes, modes | `platform-deployment` | Phase 9 |

---

## Relations Entre Composants

```
01-scenario-dsl
      │ produit ScenarioDefinition
      ▼
02-execution-engine ──────── 04-agent-runtime (mode DISTRIBUTED)
      │ orchestre              │
      │ TaskExecutors          │ via
      ▼                        ▼
03-task-framework        05-transport-layer
      │
      ├──▶ 06-injection-gatling (phase INJECTION)
      │         │ produit InjectionResult
      │         ▼
      └──▶ 07-assertion-framework (phase ASSERTION)
                │ produit AssertionResult
                ▼
          08-report-engine
                │
                └──▶ publishers (Confluence, S3, Git...)
```

---

## Modes d'Accès (même artefact)

Le fat JAR unique expose trois modes d'accès à une campagne — API REST, IHM (navigateur),
CLI headless — mais leur **disponibilité dépend du rôle runtime**. Seul l'orchestrateur expose
des surfaces d'accès ; un nœud AGENT n'expose rien (clarification utilisateur, 2026-06-23).

### Matrice canonique : mode runtime × mode d'accès

| Accès | LOCAL (orch.+agent, même JVM) | DISTRIBUTED / ORCHESTRATOR | DISTRIBUTED / AGENT |
|---|:---:|:---:|:---:|
| API (REST `/api/v1/**`) | ✓ | ✓ | ✗ |
| IHM (navigateur `:8080`) | ✓ | ✓ | ✗ |
| CLI headless (`--scenario=`) | ✓ | ✓ | ✗ |
| Serveur web (Tomcat) | ✓ | ✓ | ✗ |
| Connexion sortante au transport | — | — | ✓ (seul rôle) |

**Règle** : en LOCAL et ORCHESTRATOR, les trois modes d'accès sont disponibles depuis le même
artefact. En AGENT, la JVM démarre **sans aucun serveur web** (`WebApplicationType.NONE`, ADR-019) :
ni API, ni IHM, ni CLI. L'agent est un pur worker — il se connecte en sortant au transport
(Kafka/HTTP), s'enregistre, et exécute les `TaskExecutionRequest` reçues. Voir ADR-019 (matrice
d'accès + absence de Tomcat en AGENT) et ADR-021 (CLI headless réservé orchestrateur).

> NB : l'éventuel récepteur de transport HTTP entrant de l'agent (`agent.http.callbackUrl`,
> spec 04 §8, actif seulement si `transport.type=HTTP`) n'est PAS une surface d'accès API/IHM/CLI ;
> c'est le canal interne de réception des tâches, orthogonal aux modes d'accès ci-dessus.

### Invocation par mode d'accès (orchestrateur : LOCAL / ORCHESTRATOR)

| Mode | Invocation | Serveur | ADR |
|---|---|---|---|
| CLI headless | `java -jar platform-app.jar --scenario=x.yaml` | aucun (`WebApplicationType.NONE`) | ADR-021 |
| API | `java -jar platform-app.jar` puis `POST /api/v1/scenarios` | long-running | — |
| IHM | `java -jar platform-app.jar` (`web.ui.enabled=true`) puis navigateur sur `:8080` | long-running | ADR-019 |

En mode CLI headless, l'application parse le scénario, l'exécute, imprime un résumé sur stdout
et sort avec un code de retour (0 succès / 1 échec / 2 arguments invalides). Aucun serveur HTTP
ni IHM. En l'absence de `--scenario=`, l'application démarre en serveur long-running (API + IHM).

---

## Règles de Lecture

1. Pour implémenter un composant, lire SA spec + la spec des composants dont il dépend.
2. Les interfaces définies dans une spec ne doivent pas être modifiées sans ADR.
3. Les exemples YAML dans les specs sont des exemples de RÉFÉRENCE — les implémenter exactement.
4. Les types de retour et signatures sont définitifs à partir de la Phase 1.
5. En cas d'ambiguïté sur une spec : escalader vers l'Architect (voir `.claude/guides/agent-orchestration.md`).

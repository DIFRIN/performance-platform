# Specifications Overview — Performance Engineering Platform

> Ce fichier est le point d'entrée des specs. Il décrit les relations entre elles
> et doit être lu avant toute spec individuelle.
> Pour comprendre QUI implémente quoi et dans quel ordre, voir `guides/agent-orchestration.md`.

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

## Règles de Lecture

1. Pour implémenter un composant, lire SA spec + la spec des composants dont il dépend.
2. Les interfaces définies dans une spec ne doivent pas être modifiées sans ADR.
3. Les exemples YAML dans les specs sont des exemples de RÉFÉRENCE — les implémenter exactement.
4. Les types de retour et signatures sont définitifs à partir de la Phase 1.
5. En cas d'ambiguïté sur une spec : escalader vers l'Architect (voir `guides/agent-orchestration.md`).

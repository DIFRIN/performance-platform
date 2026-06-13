# Dependency Map — RÉFÉRENCE SYSTEM DESIGNER UNIQUEMENT

> ⚠️ ARCHIVÉ après création des PDRs et Issues par le System Designer.
> Les dépendances entre Issues sont documentées dans chaque `.claude/issues/ISSUE-XXX.md`
> (champ "Bloquée par") et dans `.claude/progress.md`.
> Ce fichier n'est plus la source de vérité une fois les Issues créées.

> Référence rapide pour savoir ce qui doit être STABLE avant de démarrer une tâche.
> Évite les blocages de session dus à une dépendance manquante découverte en cours de route.

---

## Graphe de Dépendances

```
Tâche 1.1 (Records Core)
    │
    ├──▶ Tâche 1.2 (Records Scénario + Résultats)
    │         │
    │         └──▶ Tâche 1.3 (Domain Events)
    │                   │
    │                   └──▶ Tâche 1.4 (YAML Parser)
    │                                │
    │                                └──▶ Tâche 2.1 (Ports + DAG)
    │                                           │
    │                                           └──▶ Tâche 2.2 (LocalExecutionEngine)
    │                                                       │
    │                                              ┌────────┴────────┐
    │                                              ▼                 ▼
    │                                         Tâche 2.3        Tâche 3.x
    │                                       (Persistence)    (Task Executors)
    │                                              │                 │
    │                                              └────────┬────────┘
    │                                                       ▼
    │                                              Tâche 4.x (Gatling)
    │                                                       │
    │                                              Tâche 5.x (Assertions)
    │                                                       │
    │                                              Tâche 6.x (Reporting)
    │                                                       │
    │                                    ┌──────────────────┤
    │                                    ▼                  ▼
    │                             Tâche 7.1            Tâche 7.3
    │                          (Transport Layer)    (AgentRuntime)
    │                                    │                  │
    │                                    └──────┬───────────┘
    │                                           ▼
    │                                    Tâche 7.4
    │                                (RemoteExecutionEngine)
    │                                           │
    │                                    Tâche 7.5 (E2E)
    │
    └──▶ Tâche 8.x, 9.x, 10.x (peuvent démarrer partiellement en parallèle)
```

---

## Table de Prérequis Rapide

| Tâche | Prérequis STABLE |
|---|---|
| 1.2 | 1.1 |
| 1.3 | 1.1, 1.2 |
| 1.4 | 1.1, 1.2, 1.3 |
| 2.1 | Phase 1 complète |
| 2.2 | 2.1 |
| 2.3 | 2.2 |
| 3.x | 2.3 |
| 4.x | 3.x complet |
| 5.x | 4.x |
| 6.x | 5.x |
| 7.1 | 2.2 |
| 7.3 | 7.1 |
| 7.4 | 7.1, 7.3 |
| 7.5 | 7.4 |
| 8.x | Phase 7 |
| 9.x | Phase 8 |
| 10.x | Phase 9 |

---

## Interfaces Bloquantes

Ces interfaces sont des points de couplage fort.
Si elles changent, TOUTES les tâches dépendantes doivent être re-vérifiées.

| Interface | Utilisée par |
|---|---|
| `TaskExecutor` | 3.x, 4.x, 5.x, 7.x |
| `ExecutionTransport` | 7.1, 7.2, 7.3, 7.4 |
| `ReportPublisher` | 6.3, 8.x |
| `ExecutionContext` | 2.2, 3.x, 4.x, 5.x, 7.x |
| `AssertionExecutor` | 5.x, 7.x |

# ISSUE-185 — Rewire Config + Delete Local/RemoteExecutionEngine

**PDR** : PDR-050
**Module** : `platform-execution-engine`, `platform-app`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-184
**Estime** : L (3-6h)

---

## Objectif

Réécrire la config Spring pour produire un seul `ExecutionEngine` dans les deux
modes, et supprimer `LocalExecutionEngine` + `RemoteExecutionEngine` + leur
`ExecutionEngineConfiguration` conditionnel (A1).

## Fichiers à Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/
  ├── local/LocalExecutionEngine.java         — SUPPRIMER
  ├── remote/RemoteExecutionEngine.java       — SUPPRIMER
  └── config/ExecutionEngineConfiguration.java — réécrire (un bean ExecutionEngine, StepDispatcher conditionnel)

platform-app/src/main/java/com/performance/platform/app/config/
  └── (câblage StepDispatcher LOCAL vs DISTRIBUTED via @Bean conditionnel)
```

## Règles Spécifiques

- Aucun `if/switch` sur `runtime.mode` : sélection `StepDispatcher` par `@ConditionalOnProperty` (CF-03).
- Le bean `ExecutionEngine` est identique en LOCAL et DISTRIBUTED — seul le `StepDispatcher` diffère.

## Critères de Done

- [ ] `LocalExecutionEngine`/`RemoteExecutionEngine` supprimés
- [ ] Config réécrite, smoke tests LOCAL/ORCHESTRATOR verts
- [ ] `mvn test -pl platform-execution-engine,platform-app -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-185 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (Local/Remote → ❌ REMOVED, ExecutionEngine → PLANNED)

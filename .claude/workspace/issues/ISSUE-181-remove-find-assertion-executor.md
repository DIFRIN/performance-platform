# ISSUE-181 — Remove TaskExecutorLookup.findAssertionExecutor

**PDR** : PDR-037
**Module** : `platform-execution-engine`, `platform-app`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : —
**Estime** : M (1-3h)

---

## Objectif

Finaliser la migration assertion (A2) : supprimer `TaskExecutorLookup.findAssertionExecutor`
(déprécié) et faire résoudre `RegistryTaskExecutorLookup` les assertions via
`TaskExecutorRegistry` uniquement (car `AssertionExecutor extends TaskExecutor`).

> ADR requise (ADR-029) : retrait de la méthode publique `findAssertionExecutor`.

## Fichiers à Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/local/
  └── TaskExecutorLookup.java              — SUPPRIMER findAssertionExecutor

platform-app/src/main/java/com/performance/platform/app/config/
  ├── RegistryTaskExecutorLookup.java      — SUPPRIMER findAssertionExecutor + injection AssertionExecutorRegistry
  └── RuntimeAssemblyConfiguration.java    — taskExecutorLookup(TaskExecutorRegistry) uniquement
```

## Règles Spécifiques

- `RegistryTaskExecutorLookup` ne dépend plus d'`AssertionExecutorRegistry` (déjà déprécié).
- La résolution des assertions se fait par `taskRegistry.getFor(taskName)` (ISSUE-152 déjà en place).
- Référencer la décision ISSUE-156 pour la collision de nom `database`/`database-assertion`.

## Critères de Done

- [ ] `findAssertionExecutor` supprimé de `TaskExecutorLookup` + `RegistryTaskExecutorLookup`
- [ ] `RuntimeAssemblyConfiguration` n'injecte plus `AssertionExecutorRegistry`
- [ ] `mvn test -pl platform-execution-engine,platform-app -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-181 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (findAssertionExecutor → ❌ REMOVED)

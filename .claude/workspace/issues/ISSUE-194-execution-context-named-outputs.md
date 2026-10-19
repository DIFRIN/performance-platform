# ISSUE-194 — ExecutionContext Named Outputs + Persistent Store

**PDR** : PDR-053
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : —
**Estime** : L (3-6h)

---

## Objectif

Corriger les deux défauts de `ExecutionContext` (B1) : `with()` O(store) → O(N²), et
lookup type-erasé/ordre-dépendant. Ajouter `getOutput`/`getFirstOutput` (clés nommées),
déprécier `get(Class)`/`getFirst(Class)`, et passer à un store persistant/immutable.

> ADR requise (ADR-031).

## Fichiers à Modifier

```
platform-domain/src/main/java/com/performance/platform/domain/execution/
  └── ExecutionContext.java        — getOutput/getFirstOutput + store persistant + dépréciations

platform-domain/src/test/java/com/performance/platform/domain/execution/
  └── ExecutionContextNamedOutputTest.java   — NOUVEAU
```

## Interfaces à Implémenter

```java
public <T> Optional<T> getOutput(String taskId, String agentId, String outputName, Class<T> type);
public <T> Optional<T> getFirstOutput(String taskId, String outputName, Class<T> type);
@Deprecated(forRemoval = true) public <T> Optional<T> get(String taskId, String agentId, Class<T> type);
@Deprecated(forRemoval = true) public <T> Optional<T> getFirst(String taskId, Class<T> type);
```

## Règles Spécifiques

- `with()` devient O(profondeur du chemin), plus de deep-copy O(store). Immuabilité externe conservée (CF-02).
- `TaskResult.outputs` : les clés deviennent des noms contractuels (`p95Ms`, `errorRate`, ...).

## Critères de Done

- [ ] `getOutput`/`getFirstOutput` ajoutés, `get(Class)`/`getFirst(Class)` dépréciés
- [ ] `with()` O(chemin) — test de performance/unité
- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-194 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (ExecutionContext → ⚠️ BREAKING)

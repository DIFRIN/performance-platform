# PDR-053 — ExecutionContext Named Outputs & Persistent Store

**Module Maven** : `platform-domain` (+ migration consommateurs)
**Package** : `com.performance.platform.domain.execution`
**Statut** : WAITING
**Specs de référence** : `improvements.md` B1 ; `constraints.md` CF-02
**Dépend de** : PDR-001 (ExecutionContext/TaskResult)
**Issues** : ISSUE-194, ISSUE-195

---

## Responsabilité

`ExecutionContext` est immuable mais porte deux défauts de conception :
1. **`with()` est O(store size)** → O(N²) global : deep-copy de toute la map imbriquée
   à chaque résultat.
2. **Lookup type-erasé et ordre-dépendant** : `get(taskId, agentId, Class<T>)` scanne
   `outputs.values()` et retourne la première valeur castable ; deux `String` outputs
   → résultat indéfini. `Class<T>` est utilisé comme clé alors qu'il n'en est pas une.

> ADR requise (ADR-031) : modification de l'interface publique ⚡ `ExecutionContext`
> (et `TaskResult.outputs` déjà `Map<String,Object>` — les clés deviennent significatives).

---

## Interfaces Publiques

```java
package com.performance.platform.domain.execution;

public record ExecutionContext(ExecutionId executionId, ScenarioId scenarioId,
                               Map<String, Map<String, TaskResult>> store) {

    // === existants (conservés, dépréciés progressivement) ===
    public ExecutionContext with(String taskId, String agentId, TaskResult result);
    @Deprecated(forRemoval = true) public <T> Optional<T> get(String taskId, String agentId, Class<T> type);
    @Deprecated(forRemoval = true) public <T> Optional<T> getFirst(String taskId, Class<T> type);
    public Map<String, TaskResult> getAll(String taskId);

    // === NOUVEAUX — clés de sortie nommées ===
    public <T> Optional<T> getOutput(String taskId, String agentId, String outputName, Class<T> type);
    public <T> Optional<T> getFirstOutput(String taskId, String outputName, Class<T> type);
}
```

- `store` interne passe à une structure **persistante/immutable** (ex:
  `io.vavr`-style persistent map maison ou `ConcurrentHashMap` derrière un wrapper
  safe) pour que `with()` soit O(profondeur du chemin) et non O(store). L'API
  publique immuable (copy-on-write) est préservée (CF-02).
- `TaskResult.outputs` : les clés deviennent des noms de sortie contractuels
  (`"p95Ms"`, `"errorRate"`, ...) — documentés par executor. `getOutput` lit une clé
  précise, jamais « la première valeur castable ».

---

## Règles de Comportement

- Jamais de `setXxx` : l'immutabilité externe est conservée (CF-02).
- Les anciens `get(Class<T>)`/`getFirst(Class<T>)` restent présents (dépréciés)
  pendant la migration, puis sont supprimés dans ISSUE-195.
- Les consommateurs (PartialContextBuilder, engine, reporting, assertion) migrent
  vers les clés nommées — aucun changement de comportement métier.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 (ExecutionContext, TaskResult, PartialExecutionContext)

Ce PDR est utilisé par :
  PDR-050 (engine consolidé), PDR-015 (reporting)
```

---

## Critères de Done (PDR complet)

- [ ] `getOutput`/`getFirstOutput` ajoutés + `get(Class)`/`getFirst(Class)` dépréciés (ISSUE-194)
- [ ] `with()` O(chemin), plus de deep-copy O(store) (ISSUE-194)
- [ ] Consommateurs migrés (ISSUE-195)
- [ ] `mvn test -pl platform-domain,platform-execution-engine,platform-reporting -q` → 0 erreur

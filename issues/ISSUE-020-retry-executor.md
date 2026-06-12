# ISSUE-020 — RetryExecutor (backoff exponentiel)

**PDR** : PDR-006
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-019
**Estime** : M

---

## Objectif

Implémenter `RetryExecutor` appliquant une `RetryPolicy` (backoff exponentiel plafonné).

## Fichiers à Créer

```
platform-execution-engine/src/main/java/com/performance/platform/engine/retry/
  ├── RetryExecutor.java          — interface
  └── DefaultRetryExecutor.java   — implémentation

platform-execution-engine/src/test/java/com/performance/platform/engine/retry/
  └── DefaultRetryExecutorTest.java — succès après N retries, échec après maxAttempts, calcul du délai
```

## Interfaces à Implémenter

```java
public interface RetryExecutor {
    <T> T executeWithRetry(RetryPolicy policy, Supplier<T> action);
}

@Component
public class DefaultRetryExecutor implements RetryExecutor { /* ... */ }
```

## Règles Spécifiques

- Délai de l'attempt n : `min(initialDelay × multiplier^(n-1), maxDelay)`.
- Ne retry que si l'exception ∈ `retryableExceptions` (ou si l'ensemble est vide → toutes).
- `maxAttempts` épuisé → propager la dernière exception.
- Sleeps via Virtual Threads (`Thread.sleep` acceptable sous VT).

## Critères de Done

- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] Action réussissant au 3e essai → succès
- [ ] Action échouant toujours → exception après `maxAttempts`
- [ ] Délais conformes au backoff (test avec multiplier=2.0)
- [ ] `progress.md` mis à jour : ISSUE-020 → DONE
- [ ] `context/interfaces-registry.md` : `RetryExecutor` → STABLE

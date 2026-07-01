# ISSUE-148 -- AssertionSummary Domain Record

**PDR** : PDR-034
**Module** : `platform-domain`
**Statut** : WAITING
**Priorite** : P0 (bloquant -- AssertionSummary est une dependance de PDR-035)
**Bloquee par** : rien
**Estime** : S (< 1h)

---

## Objectif

Creer le record `AssertionSummary` dans `platform-domain` -- le format de sortie unifie de toutes les assertions. Ce record est place dans `TaskResult.outputs` sous la cle `"assertion"` pour la serialisation via `ExecutionEvent`.

## Fichiers a Creer

```
platform-domain/src/main/java/com/performance/platform/domain/assertion/
  └── AssertionSummary.java       -- record immuable: assertionId, verdict, description, collectedData, history, evaluationDuration, evaluatedAt

platform-domain/src/test/java/com/performance/platform/domain/assertion/
  └── AssertionSummaryTest.java   -- tests: constructeur compact, defensive copies, isPassed(), factory of()
```

## Interfaces a Implementer

```java
// Du PDR-034, a implémenter EXACTEMENT :
public record AssertionSummary(
    TaskId assertionId,
    AssertionStatus verdict,          // PASSED | FAILED | SKIPPED | ERROR
    String description,                // human-readable justification for the report
    Map<String, Object> collectedData, // metrics/samples/data used in the decision
    List<AssertionSample> history,     // interval-based sampling history (empty list = point-in-time)
    Duration evaluationDuration,
    Instant evaluatedAt
) {
    public AssertionSummary {
        Objects.requireNonNull(assertionId, "assertionId required");
        Objects.requireNonNull(verdict, "verdict required");
        Objects.requireNonNull(description, "description required");
        Objects.requireNonNull(evaluationDuration, "evaluationDuration required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt required");
        collectedData = collectedData == null ? Map.of() : Map.copyOf(collectedData);
        history = history == null ? List.of() : List.copyOf(history);
    }

    public boolean isPassed() { return verdict == AssertionStatus.PASSED; }

    public static AssertionSummary of(
            TaskId assertionId, AssertionStatus verdict, String description,
            Map<String, Object> collectedData, Duration evaluationDuration, Instant evaluatedAt) {
        return new AssertionSummary(assertionId, verdict, description,
                collectedData, List.of(), evaluationDuration, evaluatedAt);
    }
}
```

## Regles Specifiques

- 0 annotation Spring/JPA/Jackson -- domaine pur
- Les collections (`collectedData`, `history`) doivent etre copiees defensivement dans le constructeur compact
- `AssertionStatus` est REUTILISE (pas de nouvel enum) -- il est deja dans `platform-domain`
- La factory `of()` cree un `AssertionSummary` avec history vide (pour assertions ponctuelles)

## Criteres de Done

- [ ] `mvn test -pl platform-domain -q` -> 0 erreur
- [ ] `AssertionSummary` compile (constructeur compact + factory + isPassed)
- [ ] Test : constructeur avec history non-vide -> les elements sont preserves
- [ ] Test : collectedData null -> remplace par Map.of() (pas NPE)
- [ ] Test : history null -> remplace par List.of() (pas NPE)
- [ ] Test : isPassed() retourne true pour PASSED, false pour FAILED/SKIPPED/ERROR
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-148 -> DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis a jour : AssertionSummary -> STABLE

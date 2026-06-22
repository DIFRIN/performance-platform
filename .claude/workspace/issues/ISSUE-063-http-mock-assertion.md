# ISSUE-063 — HttpMockAssertionExecutor (@Assertion name="http-mock")

**PDR** : PDR-014
**Module** : `platform-assertion`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-059
**Estime** : M

---

## Objectif

Implémenter `HttpMockAssertionExecutor` : évalue les appels reçus par un WireMock
(receivedCalls/matchedCalls/unmatchedCalls) pour un endpoint donné.

## Fichiers à Créer

```
platform-assertion/src/main/java/com/performance/platform/assertion/httpmock/
  └── HttpMockAssertionExecutor.java

platform-assertion/src/test/java/com/performance/platform/assertion/httpmock/
  └── HttpMockAssertionExecutorTest.java — WireMock de test
```

## Interfaces à Implémenter

```java
@Assertion(name = "http-mock", description = "WireMock receivedCalls/matchedCalls assertions")
public class HttpMockAssertionExecutor implements AssertionExecutor {
    public String getSupportedAssertionName() { return "http-mock"; }
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) { /* ... */ }
}
```

## Règles Spécifiques

- Params : `metric` (receivedCalls/matchedCalls/unmatchedCalls), `endpoint`, `operator`, `value`, `refTaskId`.
- Lit l'URL du mock via `context.getFirst(refTaskId, Map.class)` (outputs MockServerTaskExecutor) + appel à l'API WireMock.
- Comparaison via `AssertionOperator`. `Evidence` rempli. Erreur → ERROR.

## Critères de Done

- [ ] `mvn test -pl platform-assertion -q` → 0 erreur
- [ ] `receivedCalls >= N` évalué via WireMock de test
- [ ] `.claude/progress.md` mis à jour : ISSUE-063 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `HttpMockAssertionExecutor` → STABLE

# ISSUE-060 — GatlingMetricAssertionExecutor (@Assertion name="gatling-metric")

**PDR** : PDR-014
**Module** : `platform-assertion`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-059, ISSUE-056
**Estime** : M

---

## Objectif

Implémenter `GatlingMetricAssertionExecutor` : lit l'`InjectionResult` du contexte et évalue
une métrique (p95, errorRate, throughput...) avec un opérateur.

## Fichiers à Créer

```
platform-assertion/src/main/java/com/performance/platform/assertion/gatling/
  ├── GatlingMetricAssertionExecutor.java
  └── MetricExtractor.java   — InjectionResult + nom de métrique → double

platform-assertion/src/test/java/com/performance/platform/assertion/gatling/
  └── GatlingMetricAssertionExecutorTest.java
```

## Interfaces à Implémenter

```java
@Assertion(name = "gatling-metric", description = "Gatling metrics: p95, errorRate...")
public class GatlingMetricAssertionExecutor implements AssertionExecutor {
    public String getSupportedAssertionName() { return "gatling-metric"; }
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) { /* ... */ }
}
```

## Règles Spécifiques

- Params : `metric`, `operator`, `value`, `unit`, `refTaskId` (optionnel).
- Lit via `context.getFirst(refTaskId, InjectionResult.class)` (ou première injection si refTaskId absent).
- Métriques : p50/p75/p90/p95/p99/max/min/mean/errorRate/throughput/totalRequests/failedRequests.
- Comparaison via `AssertionOperator.evaluate(actual, expected)`.
- `Evidence` rempli ; `description` lisible ; `severity` → contribue au Verdict.
- Erreur technique → `AssertionStatus.ERROR`.

## Critères de Done

- [ ] `mvn test -pl platform-assertion -q` → 0 erreur
- [ ] p95 < seuil → PASSED ; errorRate > seuil → FAILED avec evidence
- [ ] `.claude/progress.md` mis à jour : ISSUE-060 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `GatlingMetricAssertionExecutor` → STABLE

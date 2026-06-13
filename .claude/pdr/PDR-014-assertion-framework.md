# PDR-014 — Assertion Framework

**Module Maven** : `platform-assertion`
**Package** : `com.performance.platform.assertion`
**Statut** : WAITING
**Specs de référence** : `.claude/specifications/07-assertion-framework.md` (complet)
**Dépend de** : PDR-001, PDR-003, PDR-013
**Issues** : ISSUE-059, ISSUE-060, ISSUE-061, ISSUE-062, ISSUE-063, ISSUE-064

---

## Responsabilité

Évalue les assertions post-injection en lisant l'`ExecutionContext` et des sources de données
configurées. Produit des `AssertionResult` avec `Evidence`. Fournit le registre
`AssertionExecutorRegistry` et les 5 executors : gatling-metric, database, kafka, http-mock,
file. Chaque executor porte `@Assertion(name="...")`.

---

## Interfaces Publiques

```java
public interface AssertionExecutorRegistry {
    AssertionExecutor getFor(String assertionName);
    void register(AssertionExecutor executor);
}

@Component
public class DefaultAssertionExecutorRegistry implements AssertionExecutorRegistry {
    public DefaultAssertionExecutorRegistry(List<AssertionExecutor> executors) { /* ... */ }
}
```

### Executors

```java
@Assertion(name = "gatling-metric", description = "Gatling metrics: p95, errorRate...")
public class GatlingMetricAssertionExecutor implements AssertionExecutor {
    public String getSupportedAssertionName() { return "gatling-metric"; }
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) { /* ... */ }
}

@Assertion(name = "database", description = "SQL count/exists assertions")
public class DatabaseAssertionExecutor implements AssertionExecutor { /* ... */ }

@Assertion(name = "kafka", description = "Kafka consumedCount/producedCount/lag assertions")
public class KafkaAssertionExecutor implements AssertionExecutor { /* ... */ }

@Assertion(name = "http-mock", description = "WireMock receivedCalls/matchedCalls assertions")
public class HttpMockAssertionExecutor implements AssertionExecutor { /* ... */ }

@Assertion(name = "file", description = "File EXISTS/CHECKSUM/SIZE assertions")
public class FileAssertionExecutor implements AssertionExecutor { /* ... */ }
```

---

## Règles de Comportement

- `AssertionExecutor` lit le contexte via `context.getFirst(refTaskId, type)` ou `context.getAll(refTaskId)` (multi-agent).
- `gatling-metric` : lit `InjectionResult` (clé `"result"`) ; metrics p50/p75/p90/p95/p99/max/min/mean/errorRate/throughput/totalRequests/failedRequests.
- Comparaison via `AssertionOperator.evaluate(actual, expected)`.
- `Evidence` toujours rempli : `actualValue`, `expectedValue`, `operator`, `unit`, `details`.
- `description` lisible : ex `"errorRate 0.5% < 1.0% → PASSED"`.
- `severity` (WARNING|ERROR, default ERROR) lu depuis `step.parameters()` — contribue au Verdict (WARNING vs FAILED).
- Erreur technique d'évaluation → `AssertionStatus.ERROR` (pas une exception propagée).
- `database`/`kafka`/`http-mock` : accès I/O sous Virtual Threads.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → AssertionResult, AssertionStatus, AssertionOperator, Evidence, Verdict,
            ExecutionContext, StepDefinition, TaskId, InjectionResult
  PDR-003 → AssertionExecutor, @Assertion
  PDR-013 → InjectionResult (structure des outputs gatling)

Ce PDR est utilisé par :
  PDR-009 (agent runtime) → executors enregistrés (phase ASSERTION)
  PDR-015 (reporting)     → AssertionReportEntry, contribution au Verdict
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Tests : chaque operator, chaque type d'assertion, evidence correcte
- [ ] Test Testcontainers PostgreSQL pour DatabaseAssertionExecutor
- [ ] Interfaces dans `.claude/context/interfaces-registry.md` STABLE

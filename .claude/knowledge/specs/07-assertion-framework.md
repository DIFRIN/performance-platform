# Spec 07 — Assertion Framework

**Module** : `platform-assertion`  
**Dépend de** : `platform-domain`, `platform-application`

---

## 1. Objectif

Évaluer des assertions post-injection en lisant l'`ExecutionContext`
et les sources de données configurées. Produire des `AssertionResult`
avec une `Evidence` détaillée.

---

## 2. Interfaces

```java
public interface AssertionExecutor {
    AssertionResult evaluate(ExecutionContext context, StepDefinition step);
    String getSupportedAssertionName();   // correspond à l'annotation @Assertion(name="...")
}

public interface AssertionExecutorRegistry {
    AssertionExecutor getFor(String assertionName);
    void register(AssertionExecutor executor);
}
```

---

## 3. AssertionResult

```java
public record AssertionResult(
    TaskId assertionId,
    AssertionStatus status,         // PASSED | FAILED | SKIPPED | ERROR
    String description,             // "errorRate 0.5% < 1.0% → PASSED"
    Evidence evidence,
    Duration evaluationDuration,
    Instant evaluatedAt
) {
    public boolean isPassed() { return status == AssertionStatus.PASSED; }
}

public record Evidence(
    Object actualValue,             // valeur mesurée
    Object expectedValue,           // valeur attendue
    AssertionOperator operator,
    String unit,                    // "ms", "%", "count"...
    Map<String, Object> details     // données brutes supplémentaires
) {}
```

---

## 4. Types d'Assertions et YAML

### GATLING_METRIC
```yaml
type: GATLING_METRIC
metric: p95              # p50 | p75 | p90 | p95 | p99 | max | min | mean
                         # errorRate | throughput | totalRequests | failedRequests
operator: LT             # LT | LTE | GT | GTE | EQ | NEQ
value: 500
unit: ms                 # ms | percent | req_per_sec | count
refTaskId: customer-api-load  # taskId de l'injection Gatling (optionnel si 1 seule injection)
```

Lit depuis : `context.getFirst(step.parameters().get("refTaskId").toString(), InjectionResult.class)`
ou `context.getAll(refTaskId)` pour itérer sur les résultats multi-agents

### DATABASE
```yaml
type: DATABASE
datasource: customer-db
query: "SELECT COUNT(*) FROM orders WHERE status = 'CREATED'"
operator: GT
value: 1000
unit: count
```

### KAFKA
```yaml
type: KAFKA
metric: consumedCount    # producedCount | consumedCount | lag
topic: customer-events
groupId: perf-monitor
operator: GT
value: 5000
refTaskId: kafka-monitor   # taskId du KafkaConsumerTask
```

Lit depuis : `context.getFirst(refTaskId, Map.class)`

### HTTP_MOCK
```yaml
type: HTTP_MOCK
metric: receivedCalls    # receivedCalls | matchedCalls | unmatchedCalls
endpoint: POST /api/customers
operator: GTE
value: 1000
refTaskId: start-mock
```

Lit depuis : `context.getFirst(refTaskId, Map.class)` + appel WireMock API

### FILE
```yaml
type: FILE
path: /reports/output.csv
check: EXISTS             # EXISTS | NOT_EXISTS | CHECKSUM | SIZE_GT | SIZE_LT
checksum: sha256:abc123   # pour CHECKSUM
sizeBytes: 1024           # pour SIZE_GT / SIZE_LT
```

---

## 5. AssertionOperator

```java
public enum AssertionOperator {
    LT, LTE, GT, GTE, EQ, NEQ;

    public boolean evaluate(double actual, double expected) {
        return switch (this) {
            case LT  -> actual < expected;
            case LTE -> actual <= expected;
            case GT  -> actual > expected;
            case GTE -> actual >= expected;
            case EQ  -> actual == expected;
            case NEQ -> actual != expected;
        };
    }
}
```

---

## 6. Contribution au Verdict Final

```java
public enum Verdict {
    SUCCESS,   // toutes les assertions PASSED
    WARNING,   // au moins une assertion FAILED marquée severity: WARNING
    FAILED     // au moins une assertion FAILED marquée severity: ERROR (default)
}
```

Chaque assertion peut déclarer `severity: WARNING | ERROR` (default ERROR).

# ISSUE-006 — Records ExecutionPlan / ExecutionStep / ExecutionState + Injection/Assertion VOs

**PDR** : PDR-001
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-003, ISSUE-005
**Estime** : M

---

## Objectif

Créer les records d'exécution (`ExecutionPlan`, `ExecutionStep`, `ExecutionState`) et les
value objects résultats (`InjectionResult`, `AssertionResult`, `Evidence`).

## Fichiers à Créer

```
platform-domain/src/main/java/com/performance/platform/domain/execution/
  ├── ExecutionPlan.java
  ├── ExecutionStep.java
  └── ExecutionState.java
platform-domain/src/main/java/com/performance/platform/domain/injection/
  └── InjectionResult.java
platform-domain/src/main/java/com/performance/platform/domain/assertion/
  ├── AssertionResult.java
  └── Evidence.java

platform-domain/src/test/java/com/performance/platform/domain/execution/
  └── ExecutionPlanTest.java
platform-domain/src/test/java/com/performance/platform/domain/assertion/
  └── AssertionResultTest.java — isPassed()
```

## Interfaces à Implémenter

```java
public record ExecutionStep(StepDefinition step, List<TaskId> dependencies, int dagLevel, Set<String> requiredContextKeys) {}

public record ExecutionPlan(ExecutionId id, ScenarioId scenarioId,
    List<ExecutionStep> preparationSteps, List<ExecutionStep> injectionSteps,
    List<ExecutionStep> assertionSteps, ExecutionContext initialContext) {}

public record ExecutionState(ExecutionId id, ScenarioId scenarioId, ExecutionStatus status,
    Map<Phase, PhaseStatus> phaseStatuses, ExecutionContext context, Instant startedAt, Instant updatedAt) {}

public record InjectionResult(TaskId taskId, String simulationClass, Duration duration,
    long totalRequests, long successfulRequests, long failedRequests, double errorRate,
    double throughput, long p50Ms, long p75Ms, long p90Ms, long p95Ms, long p99Ms,
    long maxMs, long minMs, double meanMs, Path gatlingReportDirectory, Map<String, Object> rawStats) {}

public record AssertionResult(TaskId assertionId, AssertionStatus status, String description,
    Evidence evidence, Duration evaluationDuration, Instant evaluatedAt) {
    public boolean isPassed() { return status == AssertionStatus.PASSED; }
}

public record Evidence(Object actualValue, Object expectedValue, AssertionOperator operator,
    String unit, Map<String, Object> details) {
    public Evidence { details = details == null ? Map.of() : Map.copyOf(details); }
}
```

## Règles Spécifiques

- `errorRate` en pourcentage (0.0–100.0).
- `AssertionResult.isPassed()` true ssi `status == PASSED`.
- Collections copiées défensivement où indiqué.

## Critères de Done

- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] `AssertionResult` avec status PASSED → `isPassed()` true
- [ ] `progress.md` mis à jour : ISSUE-006 → DONE
- [ ] `context/interfaces-registry.md` : `ExecutionPlan`, `ExecutionStep`, `ExecutionState`, `InjectionResult`, `AssertionResult`, `Evidence` → STABLE

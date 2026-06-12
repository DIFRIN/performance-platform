# ISSUE-074 — ExecutionMetrics (Micrometer)

**PDR** : PDR-017
**Module** : `platform-observability`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-001
**Estime** : M

---

## Objectif

Créer le module `platform-observability` et implémenter `ExecutionMetrics` (Micrometer)
exposant les 4 métriques obligatoires.

## Fichiers à Créer

```
platform-observability/pom.xml — dépend de domain, micrometer, otel
platform-observability/src/main/java/com/performance/platform/observability/metrics/
  ├── ExecutionMetrics.java
  └── MicrometerExecutionMetrics.java

platform-observability/src/test/java/com/performance/platform/observability/metrics/
  └── MicrometerExecutionMetricsTest.java — SimpleMeterRegistry
```

## Interfaces à Implémenter

```java
public interface ExecutionMetrics {
    void recordExecutionDuration(ExecutionId id, Duration duration);
    void recordTaskDuration(TaskId taskId, String taskName, Duration duration);
    void incrementTaskFailure(TaskId taskId, String taskName);
    void recordPhaseDuration(Phase phase, Duration duration);
}

@Component
public class MicrometerExecutionMetrics implements ExecutionMetrics {
    public MicrometerExecutionMetrics(MeterRegistry registry) { /* ... */ }
}
```

## Règles Spécifiques

- Métriques : `execution_duration`, `task_duration`, `task_failures_total`, `phase_duration`.
- `task_failures_total` taggé par `taskName` (String) — pas de `TaskType`.
- Tester avec `SimpleMeterRegistry`.

## Critères de Done

- [ ] `mvn test -pl platform-observability -q` → 0 erreur
- [ ] Les 4 métriques sont enregistrées avec les bons tags
- [ ] `progress.md` mis à jour : ISSUE-074 → DONE
- [ ] `context/interfaces-registry.md` : `ExecutionMetrics` → STABLE

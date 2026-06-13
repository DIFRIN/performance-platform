# PDR-017 — Observability

**Module Maven** : `platform-observability`
**Package** : `com.performance.platform.observability`
**Statut** : WAITING
**Specs de référence** : `.claude/constraints.md` CNF-04, `.claude/architecture.md` §4
**Dépend de** : PDR-001, PDR-002
**Issues** : ISSUE-074, ISSUE-075, ISSUE-076

---

## Responsabilité

Expose les métriques Micrometer, les traces OpenTelemetry, et configure le logging
structuré JSON. Écoute les domain events (PDR-002) pour incrémenter les compteurs et
mesurer les durées. Aucune logique métier — purement observabilité transversale.

---

## Interfaces Publiques

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

// Écoute les events et alimente les métriques + traces
@Component
public class ObservabilityEventListener {
    @EventListener public void on(TaskCompleted event) { /* ... */ }
    @EventListener public void on(TaskFailed event)    { /* ... */ }
    @EventListener public void on(PhaseCompleted event){ /* ... */ }
    @EventListener public void on(ScenarioFinished e)  { /* ... */ }
}

@Configuration
public class ObservabilityConfiguration { /* MeterRegistry, OTel tracer, JSON logging */ }
```

---

## Règles de Comportement

- Métriques obligatoires : `execution_duration`, `task_duration`, `task_failures_total`, `phase_duration`.
- Tags Micrometer : `executionId`, `scenarioId`, `taskId`, `taskName`, `agentId`, `phase`.
- Traces OTel : un span par task et par phase.
- Logs JSON structurés avec contexte MDC : `executionId, scenarioId, taskId, agentId, phase`.
- `task_failures_total` taggé par `taskName` (String) — pas de `TaskType`.
- Aucune logique métier — uniquement réactif aux events.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → ExecutionId, TaskId, Phase
  PDR-002 → TaskCompleted, TaskFailed, PhaseCompleted, ScenarioFinished

Ce PDR est utilisé par :
  PDR-018 (platform-app) → auto-config observabilité
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Test : les 4 métriques sont enregistrées sur les bons events
- [ ] `ExecutionMetrics` dans `.claude/context/interfaces-registry.md` STABLE

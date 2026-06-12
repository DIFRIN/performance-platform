# ISSUE-075 — ObservabilityEventListener (events → métriques + traces)

**PDR** : PDR-017
**Module** : `platform-observability`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-074, ISSUE-008
**Estime** : M

---

## Objectif

Implémenter `ObservabilityEventListener` qui écoute les domain events et alimente
les métriques + crée des spans OpenTelemetry.

## Fichiers à Créer

```
platform-observability/src/main/java/com/performance/platform/observability/listener/
  └── ObservabilityEventListener.java

platform-observability/src/test/java/com/performance/platform/observability/listener/
  └── ObservabilityEventListenerTest.java
```

## Interfaces à Implémenter

```java
@Component
public class ObservabilityEventListener {
    public ObservabilityEventListener(ExecutionMetrics metrics) { /* ... */ }
    @EventListener public void on(TaskCompleted event)    { /* recordTaskDuration */ }
    @EventListener public void on(TaskFailed event)       { /* incrementTaskFailure */ }
    @EventListener public void on(PhaseCompleted event)   { /* recordPhaseDuration */ }
    @EventListener public void on(ScenarioFinished event) { /* recordExecutionDuration */ }
}
```

## Règles Spécifiques

- `TaskCompleted.result().taskName()` taggé (String).
- Span OTel par task et par phase.
- Aucune logique métier — purement réactif.

## Critères de Done

- [ ] `mvn test -pl platform-observability -q` → 0 erreur
- [ ] Chaque event incrémente/mesure la bonne métrique
- [ ] `progress.md` mis à jour : ISSUE-075 → DONE
- [ ] `context/interfaces-registry.md` mis à jour

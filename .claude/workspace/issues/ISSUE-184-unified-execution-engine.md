# ISSUE-184 — Unified ExecutionEngine

**PDR** : PDR-050
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-180, ISSUE-170
**Estime** : L (3-6h)

---

## Objectif

Créer l'`ExecutionEngine` unique (A1) paramétré par `StepDispatcher`, en extrayant
les helpers dupliqués de `LocalExecutionEngine`/`RemoteExecutionEngine`, et extraire
la souscription transport en `TransportExecutionEventHandler` séparé.

## Fichiers à Créer / Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/
  ├── ExecutionEngine.java                       — NOUVEAU (ExecuteScenarioUseCase + CancelExecutionUseCase)
  └── remote/TransportExecutionEventHandler.java — NOUVEAU (ExecutionEventHandler, DISTRIBUTED)

platform-execution-engine/src/test/java/com/performance/platform/engine/
  └── ExecutionEngineTest.java                   — NOUVEAU (unitaires partagés)
```

## Interfaces à Implémenter

```java
@Service
@ConditionalOnExpression(
  "'${runtime.mode:LOCAL}'.equals('LOCAL') || ('${runtime.mode:LOCAL}'.equals('DISTRIBUTED')"
  + " && '${runtime.role:NONE}'.equals('ORCHESTRATOR'))")
public class ExecutionEngine implements ExecuteScenarioUseCase, CancelExecutionUseCase {
    public ExecutionEngine(ExecutionPlanBuilder planBuilder,
                           ExecutionRepository executionRepository,
                           ApplicationEventPublisher eventPublisher,
                           ExecutionLifecycleDispatcher lifecycleDispatcher,
                           StepDispatcher stepDispatcher);
    @Override public ExecutionId execute(ScenarioDefinition scenario);
    @Override public void cancel(ExecutionId id);
    public ExecutionStatus getStatus(ExecutionId id);
}
```

## Règles Spécifiques

- Les helpers `initializeExecution/computeVerdict/applyVerdict/finalizeExecution/...`
  sont UNIQUES (plus de duplication).
- `TransportExecutionEventHandler` gère `onTransportEvent` (claim/completed/failed/wip)
  et alimente tracker + repository + events — hors du moteur.

## Critères de Done

- [ ] `ExecutionEngine` unique compile et passe les tests unitaires
- [ ] `TransportExecutionEventHandler` extrait
- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-184 → DONE

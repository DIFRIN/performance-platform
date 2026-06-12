# ISSUE-019 — ExecutionPlanBuilder (ScenarioDefinition → ExecutionPlan + DAG levels)

**PDR** : PDR-006
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-006, ISSUE-016
**Estime** : L

---

## Objectif

Créer le module `platform-execution-engine` et implémenter `ExecutionPlanBuilder` :
transforme un `ScenarioDefinition` en `ExecutionPlan` avec steps triés par phase et par
`dagLevel`. Lève `InvalidScenarioException` si cycle.

## Fichiers à Créer

```
platform-execution-engine/pom.xml — dépend de domain, application, scenario-dsl, transport
platform-execution-engine/src/main/java/com/performance/platform/engine/plan/
  ├── ExecutionPlanBuilder.java        — interface
  ├── DefaultExecutionPlanBuilder.java — implémentation
  └── DagLevelCalculator.java          — calcule dagLevel par tri topologique

platform-execution-engine/src/test/java/com/performance/platform/engine/plan/
  ├── DefaultExecutionPlanBuilderTest.java
  └── DagLevelCalculatorTest.java
```

## Interfaces à Implémenter

```java
public interface ExecutionPlanBuilder { ExecutionPlan build(ScenarioDefinition scenario); }

@Component
public class DefaultExecutionPlanBuilder implements ExecutionPlanBuilder { /* ... */ }
```

## Règles Spécifiques

- Séparer les steps en 3 listes par `Phase` : preparation / injection / assertion.
- `dagLevel` : 0 si aucune dépendance, sinon `max(level des deps) + 1`.
- `requiredContextKeys` = `Set.copyOf(step.requiredContexts())`.
- Cycle détecté → `InvalidScenarioException` (de `platform-application`).
- `initialContext` = `ExecutionContext.initial(executionId, scenarioId)`.

## Critères de Done

- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] Steps correctement répartis par phase et ordonnés par dagLevel
- [ ] Cycle → `InvalidScenarioException`
- [ ] `progress.md` mis à jour : ISSUE-019 → DONE
- [ ] `context/interfaces-registry.md` : `ExecutionPlanBuilder` → STABLE

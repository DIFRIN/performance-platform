# ISSUE-037 — ScenarioRestart : réception signal + cleanup stateful

**PDR** : PDR-009
**Module** : `platform-agent-runtime`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-036
**Estime** : M

---

## Objectif

Implémenter la réception du `ScenarioRestartSignal` et le mécanisme de cleanup stateful
(les executors libèrent leurs ressources, l'agent repasse IDLE).

## Fichiers à Créer

```
platform-agent-runtime/src/main/java/com/performance/platform/agent/restart/
  ├── ScenarioRestartHandler.java
  └── StatefulResourceCleaner.java   — interface implémentée par les executors stateful

platform-agent-runtime/src/test/java/com/performance/platform/agent/restart/
  └── ScenarioRestartHandlerTest.java
```

## Interfaces à Implémenter

```java
public interface StatefulResourceCleaner {
    void cleanup(ExecutionId executionId);   // executionId null = tout
}

@Component
public class ScenarioRestartHandler {
    public ScenarioRestartHandler(List<StatefulResourceCleaner> cleaners) { /* ... */ }
    public void onSignal(ScenarioRestartSignal signal) { /* annuler tasks, cleanup, IDLE */ }
}
```

## Règles Spécifiques

- `onScenarioRestart` de l'agent délègue à `ScenarioRestartHandler`.
- Tous les `StatefulResourceCleaner` reçoivent `cleanup(executionId)` (ou tout si null).
- Après cleanup : annuler les tasks en cours, repasser à IDLE.
- Cleaners attendus (implémentés dans PDR-010/013) : Gatling, MockServer, Kafka, Database, Docker, Shell.

## Critères de Done

- [ ] `mvn test -pl platform-agent-runtime -q` → 0 erreur
- [ ] Signal reçu → tous les cleaners invoqués → état IDLE
- [ ] `executionId=null` → cleanup global
- [ ] `progress.md` mis à jour : ISSUE-037 → DONE
- [ ] `context/interfaces-registry.md` : `StatefulResourceCleaner` → STABLE

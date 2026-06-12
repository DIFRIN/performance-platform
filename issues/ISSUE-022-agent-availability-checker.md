# ISSUE-022 — AgentAvailabilityChecker

**PDR** : PDR-006
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-019, ISSUE-013
**Estime** : M

---

## Objectif

Implémenter `AgentAvailabilityChecker` qui vérifie/attend la présence d'un agent compétent
via `AgentRegistryPort`, sans aucune sélection (ADR-008).

## Fichiers à Créer

```
platform-execution-engine/src/main/java/com/performance/platform/engine/availability/
  ├── AgentAvailabilityChecker.java          — interface
  └── DefaultAgentAvailabilityChecker.java   — implémentation

platform-execution-engine/src/test/java/com/performance/platform/engine/availability/
  └── DefaultAgentAvailabilityCheckerTest.java
```

## Interfaces à Implémenter

```java
public interface AgentAvailabilityChecker {
    void awaitAgentFor(String taskName, Duration taskAvailabilityTimeout);  // NoAvailableAgentException si timeout
    boolean hasAgentFor(String taskName);
}

@Component
public class DefaultAgentAvailabilityChecker implements AgentAvailabilityChecker {
    public DefaultAgentAvailabilityChecker(AgentRegistryPort registry) { /* ... */ }
}
```

## Règles Spécifiques

- `awaitAgentFor` : poll `registry.hasAgentFor(taskName)` jusqu'au timeout (NOTE R5 : ≥120s en K8s).
- Timeout atteint sans agent → `NoAvailableAgentException` (de `platform-application`).
- AUCUNE sélection d'agent — uniquement vérification de présence.
- Attente sous Virtual Threads.

## Critères de Done

- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] Agent présent → retour immédiat ; absent jusqu'au timeout → `NoAvailableAgentException`
- [ ] `progress.md` mis à jour : ISSUE-022 → DONE
- [ ] `context/interfaces-registry.md` : `AgentAvailabilityChecker` → STABLE

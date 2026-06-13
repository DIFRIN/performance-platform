# ISSUE-035 — AgentRegistry (côté orchestrateur, présence seulement)

**PDR** : PDR-009
**Module** : `platform-agent-runtime`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-034, ISSUE-013
**Estime** : M

---

## Objectif

Implémenter `AgentRegistry` (extends `AgentRegistryPort`) côté orchestrateur : suit la
présence des agents et leur TTL, SANS aucune sélection (ADR-008).

## Fichiers à Créer

```
platform-agent-runtime/src/main/java/com/performance/platform/agent/registry/
  ├── AgentRegistry.java
  ├── InMemoryAgentRegistry.java
  └── AgentTtlMonitor.java   — détecte expiration TTL → onAgentExpired

platform-agent-runtime/src/test/java/com/performance/platform/agent/registry/
  └── InMemoryAgentRegistryTest.java — register, heartbeat refresh TTL, expiration, findByTaskName
```

## Interfaces à Implémenter

```java
public interface AgentRegistry extends AgentRegistryPort { }

@Component
@ConditionalOnProperty(name = "runtime.role", havingValue = "ORCHESTRATOR")
public class InMemoryAgentRegistry implements AgentRegistry { /* ConcurrentHashMap */ }
```

## Règles Spécifiques

- `findByTaskName` retourne TOUS les agents dont `supportedTaskNames` contient le name — pas de sélection.
- `onAgentHeartbeat` rafraîchit le TTL.
- `AgentTtlMonitor` : sans heartbeat pendant `registrationTtl` → `onAgentExpired()` + publie `AgentLost`.
- Thread-safe.

## Critères de Done

- [ ] `mvn test -pl platform-agent-runtime -q` → 0 erreur
- [ ] `findByTaskName` retourne tous les agents compétents
- [ ] TTL expiré → `onAgentExpired` déclenché
- [ ] `.claude/progress.md` mis à jour : ISSUE-035 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `AgentRegistry` → STABLE

# PDR-009 — Agent Runtime

**Module Maven** : `platform-agent-runtime`
**Package** : `com.performance.platform.agent`
**Statut** : WAITING
**Specs de référence** : `specifications/04-agent-runtime.md` (complet), ADR-008, ADR-011
**Dépend de** : PDR-001, PDR-002, PDR-004, PDR-007
**Issues** : ISSUE-033, ISSUE-034, ISSUE-035, ISSUE-036, ISSUE-037, ISSUE-038

---

## Responsabilité

Implémente le cycle de vie de l'agent : enregistrement déclaratif, heartbeat, réception
broadcast + filtre de spécialisation, exécution locale via `TaskExecutorRegistry`, publication
des events (claim/progress/result), réaction au `ScenarioRestartSignal` (cleanup stateful).
Inclut côté orchestrateur le `AgentRegistry` (présence, pas de sélection) et `LocalAgent`
(mode LOCAL). Inclut `TaskSpecializationFilter`.

---

## Interfaces Publiques

```java
public interface AgentRuntime {
    void start();
    void stop();
    AgentState getState();
    AgentDescriptor getDescriptor();
    boolean canExecute(String taskName);
    void onScenarioRestart(ScenarioRestartSignal signal);
}

public interface AgentRegistrationPort {
    void register(AgentDescriptor descriptor) throws RegistrationException;
    void deregister(AgentId agentId);
    void sendHeartbeat(AgentId agentId, AgentHeartbeat heartbeat);
}

// Côté orchestrateur — adapte AgentRegistryPort (PDR-004)
public interface AgentRegistry extends AgentRegistryPort { }

public interface TaskSpecializationFilter {
    TaskFilterResult filter(TaskExecutionRequest request);
}

public sealed interface TaskFilterResult
        permits TaskFilterResult.Responsible, TaskFilterResult.NotResponsible {
    record Responsible(MessageId messageId, AgentId agentId) implements TaskFilterResult {}
    record NotResponsible(MessageId messageId, AgentId agentId) implements TaskFilterResult {}
}

public class RegistrationException extends RuntimeException {
    public RegistrationException(String message, Throwable cause) { super(message, cause); }
}
```

### Implémentations

```java
@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
@ConditionalOnProperty(name = "runtime.role", havingValue = "AGENT")
public class DistributedAgentRuntime implements AgentRuntime { /* ... */ }

@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
public class LocalAgent implements AgentRuntime {
    // supportedTaskNames = tous les noms de TaskExecutorRegistry
    // filter = toujours RESPONSIBLE ; completionPolicy = FIRST_COMPLETE
}

@Component
@ConditionalOnProperty(name = "runtime.role", havingValue = "ORCHESTRATOR")
public class InMemoryAgentRegistry implements AgentRegistry { /* ... */ }

@Component
public class DefaultTaskSpecializationFilter implements TaskSpecializationFilter { /* ... */ }
```

---

## Règles de Comportement

- Lifecycle : OFFLINE → REGISTERING → IDLE → EXECUTING → (DRAINING) → OFFLINE. Restart signal → cleanup() → IDLE.
- `supportedTaskNames` déclaré STATIQUEMENT à la config — immuable après démarrage (ADR-008).
- `TaskSpecializationFilter.filter()` : `Responsible` si `supportedTaskNames.contains(request.step().taskName())`, sinon `NotResponsible`.
- Multi-claim (ADR-011) : chaque agent spécialisé publie `TaskClaimedByAgent` indépendamment, exécute, publie `TaskCompleted`.
- Heartbeat toutes les `heartbeat.intervalSeconds` ; `ttl >= 3 × interval`.
- `onScenarioRestart()` : annule task en cours pour l'executionId (ou tous si null), libère ressources stateful, repasse IDLE.
- `TaskWorkInProgress` publié à intervalles ≤ `taskExecutionTimeout / 3` (NOTE R6).
- `AgentRegistry` : présence uniquement, AUCUNE sélection (pas de `AgentAllocator`).
- Enregistrement via transport : publie `ExecutionEvent(AGENT_REGISTERED, AgentDescriptor)`.
- I/O bloquant → Virtual Threads.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → AgentDescriptor, AgentId, AgentState, AgentHeartbeat, AgentCapabilities, MessageId
  PDR-002 → ScenarioRestartSignal, AgentRegistered, TaskClaimedByAgent, TaskWorkInProgress
  PDR-004 → AgentRegistryPort
  PDR-007 → ExecutionTransport, TaskExecutionRequest, ExecutionEvent

Ce PDR est utilisé par :
  PDR-006 (execution engine) → AgentAvailabilityChecker s'appuie sur AgentRegistry
  PDR-010 (task executors)   → l'agent invoque TaskExecutorRegistry
  PDR-018 (platform-app)     → assemblage des modes
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Tests : filter Responsible/NotResponsible, lifecycle, restart cleanup, multi-claim
- [ ] Interfaces dans `context/interfaces-registry.md` STABLE

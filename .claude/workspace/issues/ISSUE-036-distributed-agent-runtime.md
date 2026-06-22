# ISSUE-036 — DistributedAgentRuntime (lifecycle + réception + exécution)

**PDR** : PDR-009
**Module** : `platform-agent-runtime`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-033, ISSUE-034
**Estime** : L

---

## Objectif

Implémenter `DistributedAgentRuntime` : lifecycle complet, réception broadcast + filtre,
exécution locale via `TaskExecutorRegistry`, publication des events (claim/progress/result).

## Fichiers à Créer

```
platform-agent-runtime/src/main/java/com/performance/platform/agent/runtime/
  ├── AgentRuntime.java
  └── DistributedAgentRuntime.java

platform-agent-runtime/src/test/java/com/performance/platform/agent/runtime/
  └── DistributedAgentRuntimeTest.java — claim sur match, ignore sinon, publie events
```

## Interfaces à Implémenter

```java
public interface AgentRuntime {
    void start();
    void stop();
    AgentState getState();
    AgentDescriptor getDescriptor();
    boolean canExecute(String taskName);
    void onScenarioRestart(ScenarioRestartSignal signal);
}

@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
@ConditionalOnProperty(name = "runtime.role", havingValue = "AGENT")
public class DistributedAgentRuntime implements AgentRuntime {
    // deps : ExecutionTransport, TaskSpecializationFilter, AgentRegistrationPort,
    //        TaskExecutorRegistry (résolution executor par taskName)
}
```

## Règles Spécifiques

- Lifecycle : OFFLINE → REGISTERING → IDLE → EXECUTING → (DRAINING) → OFFLINE.
- `receiveTask` : passer par `TaskSpecializationFilter` ; si `Responsible` → publier `TaskClaimedByAgent`, exécuter, publier `TaskWorkInProgress` puis `TaskCompleted`/`TaskFailed`.
- Multi-claim (ADR-011) : exécution indépendante, pas de coordination entre agents.
- Idempotence : ignorer un `TaskExecutionRequest` avec un MessageId déjà traité.
- `TaskWorkInProgress` à intervalles ≤ taskExecutionTimeout/3.
- I/O sous Virtual Threads.

## Critères de Done

- [ ] `mvn test -pl platform-agent-runtime -q` → 0 erreur
- [ ] Task supportée → claim + completion ; non supportée → ignorée
- [ ] MessageId dupliqué → pas de double exécution
- [ ] `.claude/progress.md` mis à jour : ISSUE-036 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `AgentRuntime`, `DistributedAgentRuntime` → STABLE

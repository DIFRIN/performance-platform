# ISSUE-186 — Unified TaskAgentRuntime

**PDR** : PDR-050
**Module** : `platform-agent-runtime`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-180
**Estime** : L (3-6h)

---

## Objectif

Créer le `TaskAgentRuntime` unique (A1) paramétré par `ExecutionTransport` +
`AgentRegistrationPort` optionnel, fusionnant `LocalAgent` et `DistributedAgentRuntime`
(champs et méthodes identiques à ~63 %).

## Fichiers à Créer

```
platform-agent-runtime/src/main/java/com/performance/platform/agent/runtime/
  └── TaskAgentRuntime.java                  — NOUVEAU

platform-agent-runtime/src/test/java/com/performance/platform/agent/runtime/
  └── TaskAgentRuntimeTest.java              — NOUVEAU (LOCAL + DISTRIBUTED)
```

## Interfaces à Implémenter

```java
public class TaskAgentRuntime implements AgentRuntime {
    public TaskAgentRuntime(ExecutionTransport transport,
                            TaskSpecializationFilter filter,
                            AgentDescriptor descriptor,
                            Duration taskExecutionTimeout,
                            List<TaskExecutor> taskExecutors,
                            List<StatefulResourceCleaner> cleaners,
                            AgentRegistrationPort registrationPort,   // null en LOCAL
                            Duration heartbeatInterval);              // ignoré si null
    @Override public void start();
    @Override public void stop();
    @Override public AgentState getState();
    @Override public AgentDescriptor getDescriptor();
    @Override public boolean canExecute(String taskName);
    @Override public void onScenarioRestart(ScenarioRestartSignal signal);
    @Override public void onLifecycleSignal(ExecutionLifecycleSignal signal);
}
```

## Règles Spécifiques

- LOCAL : `registrationPort == null` → pas de REGISTERING, pas de heartbeat, pas de deregister.
- DISTRIBUTED : `registrationPort != null` → register + heartbeat + deregister.
- Les champs concurrents (`activeTaskCount`, `processedMessageIds`, `activeTasks`, ...) sont UNIQUES.

## Critères de Done

- [ ] `TaskAgentRuntime` unique compile et passe les tests
- [ ] `mvn test -pl platform-agent-runtime -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-186 → DONE

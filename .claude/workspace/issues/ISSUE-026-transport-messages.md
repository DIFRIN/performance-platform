# ISSUE-026 — TaskExecutionRequest + ExecutionEvent (records transport)

**PDR** : PDR-007
**Module** : `platform-transport`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-025
**Estime** : S

---

## Objectif

Créer les records de transport `TaskExecutionRequest` (remplace `TaskMessage`) et
`ExecutionEvent` avec ses constantes de type.

## Fichiers à Créer

```
platform-transport/src/main/java/com/performance/platform/transport/message/
  ├── TaskExecutionRequest.java
  └── ExecutionEvent.java

platform-transport/src/test/java/com/performance/platform/transport/message/
  └── TransportMessagesTest.java — instanciation + constantes
```

## Interfaces à Implémenter

```java
public record TaskExecutionRequest(
    MessageId id, ExecutionId executionId, StepDefinition step,
    PartialExecutionContext context, Instant dispatchedAt, RetryPolicy retryPolicy
) {}

public record ExecutionEvent(
    EventId id, ExecutionId executionId, MessageId correlationId, AgentId agentId,
    String eventType, Map<String, Object> payload, Instant occurredAt
) {
    public static final String AGENT_REGISTERED      = "AgentRegistered";
    public static final String AGENT_HEARTBEAT       = "AgentHeartbeat";
    public static final String AGENT_DEREGISTERED    = "AgentDeregistered";
    public static final String TASK_CLAIMED          = "TaskClaimedByAgent";
    public static final String TASK_WORK_IN_PROGRESS = "TaskWorkInProgress";
    public static final String TASK_COMPLETED        = "TaskCompleted";
    public static final String TASK_FAILED           = "TaskFailed";
}
```

## Règles Spécifiques

- PAS de `targetAgentId` (broadcast). PAS de classe `TaskMessage`.
- `payload` copié défensivement.

## Critères de Done

- [ ] `mvn test -pl platform-transport -q` → 0 erreur
- [ ] Aucune occurrence de `TaskMessage`
- [ ] `.claude/progress.md` mis à jour : ISSUE-026 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `TaskExecutionRequest`, `ExecutionEvent` → STABLE

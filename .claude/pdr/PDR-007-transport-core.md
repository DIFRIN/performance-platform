# PDR-007 — Transport Layer Core

**Module Maven** : `platform-transport`
**Package** : `com.performance.platform.transport`
**Statut** : WAITING
**Specs de référence** : `.claude/specifications/05-transport-layer.md` §1-3, §5-7, ADR-002, ADR-008
**Dépend de** : PDR-001, PDR-002
**Issues** : ISSUE-025, ISSUE-026, ISSUE-027

---

## Responsabilité

Définit l'abstraction `ExecutionTransport` (interface publique critique ⚡) et les
messages/records associés (`TaskExecutionRequest`, `ExecutionEvent`). Fournit la factory
de sélection par configuration et l'implémentation `InMemoryExecutionTransport` (mode LOCAL
+ tests). Les implémentations broker (Kafka/RabbitMQ/HTTP/Socket) sont dans PDR-008.

**Modèle** : broadcast + filtre côté agent. Pas de `targetAgentId`. gRPC NON implémenté.

---

## Interfaces Publiques

```java
public interface ExecutionTransport {
    void dispatchTask(TaskExecutionRequest request);
    void broadcastSignal(AgentSignal signal);
    void publishEvent(ExecutionEvent event);
    Subscription subscribe(ExecutionEventHandler handler);
    void receiveTask(TaskRequestHandler handler);
    void receiveSignal(AgentSignalHandler handler);
    void connect() throws TransportException;
    void disconnect();
    boolean isConnected();
    TransportType getType();
}

@FunctionalInterface
public interface TaskRequestHandler { void onRequest(TaskExecutionRequest request); }

@FunctionalInterface
public interface AgentSignalHandler { void onSignal(AgentSignal signal); }

@FunctionalInterface
public interface ExecutionEventHandler { void onEvent(ExecutionEvent event); }

public interface Subscription { void cancel(); boolean isActive(); }

public class TransportException extends RuntimeException {
    public TransportException(String message, Throwable cause) { super(message, cause); }
}
```

### Messages / Events

```java
public record TaskExecutionRequest(
    MessageId id,                     // clé d'idempotence côté agent
    ExecutionId executionId,
    StepDefinition step,
    PartialExecutionContext context,
    Instant dispatchedAt,
    RetryPolicy retryPolicy
) {}

public record ExecutionEvent(
    EventId id,
    ExecutionId executionId,
    MessageId correlationId,
    AgentId agentId,
    String eventType,
    Map<String, Object> payload,
    Instant occurredAt
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

### InMemory transport

```java
@Component
@ConditionalOnProperty(name = "transport.type", havingValue = "IN_MEMORY")
public class InMemoryExecutionTransport implements ExecutionTransport { /* queues en mémoire */ }
```

---

## Règles de Comportement

- `ExecutionTransport` est interface publique ⚡ : toute modification = ADR.
- `dispatchTask()` : broadcast pur, jamais de ciblage agent.
- Idempotence côté agent via `TaskExecutionRequest.id` (MessageId).
- `InMemoryExecutionTransport` : `asyncDelivery=false` par défaut (synchrone, déterministe pour tests).
- `TransportType` n'a PAS de valeur GRPC.
- I/O bloquant → Virtual Threads.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → MessageId, EventId, ExecutionId, AgentId, StepDefinition,
            PartialExecutionContext, RetryPolicy, TransportType
  PDR-002 → AgentSignal, ScenarioRestartSignal

Ce PDR est utilisé par :
  PDR-006 (execution engine) → dispatchTask, subscribe
  PDR-008 (transport impls)  → étend InMemory / implémente ExecutionTransport
  PDR-009 (agent runtime)    → receiveTask, publishEvent
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] `InMemoryExecutionTransport` : test dispatch→receive→publish→subscribe round-trip
- [ ] `ExecutionTransport` ⚡ dans `.claude/context/interfaces-registry.md` STABLE

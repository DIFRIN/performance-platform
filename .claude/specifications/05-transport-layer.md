# Spec 05 — Transport Layer

**Module** : `platform-transport`
**Dépend de** : `platform-domain`

---

## 1. Objectif

Abstraire complètement le mécanisme de communication entre Orchestrator et Agents.
Changer de transport = changer une ligne de config. Zéro changement de code.

Le transport implémente un modèle **broadcast + filtre côté agent** :
l'orchestrateur publie une `TaskExecutionRequest` sans ciblage.
Chaque agent décide localement s'il est concerné (voir `TaskSpecializationFilter` en spec 04).

---

## 2. Interface Principale

```java
public interface ExecutionTransport {

    /**
     * Dispatche une demande d'exécution en broadcast vers tous les agents.
     * L'orchestrateur ne cible pas d'agent spécifique.
     * Chaque agent filtre localement par spécialisation.
     *
     * Kafka/AMQP : publication sur le topic/exchange tasks → tous les agents reçoivent.
     * HTTP : envoi à tous les agents enregistrés supportant taskName (broadcast HTTP).
     */
    void dispatchTask(TaskExecutionRequest request);

    /**
     * Publier un signal vers tous les agents (ScenarioRestartSignal, etc.).
     * Broadcast non filtré — tous les agents reçoivent.
     */
    void broadcastSignal(AgentSignal signal);

    /**
     * Publier un event d'exécution (côté agent → orchestrateur).
     * Ex: TaskClaimedByAgent, TaskWorkInProgress, TaskCompleted, TaskFailed.
     */
    void publishEvent(ExecutionEvent event);

    /**
     * S'abonner aux events (côté orchestrateur).
     * Reçoit TaskClaimedByAgent, TaskWorkInProgress, TaskCompleted, TaskFailed,
     * AgentRegistered, AgentHeartbeat.
     */
    Subscription subscribe(ExecutionEventHandler handler);

    /**
     * Recevoir les demandes de task (côté agent).
     * Bloquant ou push selon l'implémentation.
     * L'agent appelle TaskSpecializationFilter avant d'exécuter.
     */
    void receiveTask(TaskRequestHandler handler);

    /**
     * Recevoir les signaux broadcast (côté agent).
     * Ex: ScenarioRestartSignal.
     */
    void receiveSignal(AgentSignalHandler handler);

    /** Lifecycle */
    void connect() throws TransportException;
    void disconnect();
    boolean isConnected();
    TransportType getType();
}

@FunctionalInterface
public interface TaskRequestHandler {
    void onRequest(TaskExecutionRequest request);
}

@FunctionalInterface
public interface AgentSignalHandler {
    void onSignal(AgentSignal signal);
}

@FunctionalInterface
public interface ExecutionEventHandler {
    void onEvent(ExecutionEvent event);
}
```

---

## 3. Messages et Events

### TaskExecutionRequest (remplace TaskMessage)

```java
/**
 * Demande d'exécution d'une task, diffusée en broadcast.
 * Pas de targetAgentId — la sélection appartient aux agents.
 */
public record TaskExecutionRequest(
    MessageId id,                     // clé d'idempotence côté agent
    ExecutionId executionId,
    StepDefinition step,              // contient taskName, phase, parameters

    /**
     * Contexte partiel — uniquement les clés déclarées dans step.requiredContexts().
     * L'orchestrateur construit ce sous-ensemble depuis l'ExecutionContext global.
     * NOTE R4 : pour des résultats Gatling volumineux (rawStats), ce contexte peut
     * dépasser 1 MB. Acceptable compte tenu des ressources JVM cibles (2-4 GB).
     * À surveiller si des steps accumulent de nombreux requiredContexts avec InjectionResult.
     */
    PartialExecutionContext context,

    Instant dispatchedAt,
    RetryPolicy retryPolicy
) {}
```

### ExecutionEvent

```java
/**
 * Event publié par un agent vers l'orchestrateur.
 * eventType détermine la structure du payload.
 */
public record ExecutionEvent(
    EventId id,
    ExecutionId executionId,
    MessageId correlationId,          // MessageId de la TaskExecutionRequest correspondante
    AgentId agentId,                  // agent qui publie l'event
    String eventType,                 // voir constantes ci-dessous
    Map<String, Object> payload,
    Instant occurredAt
) {
    // Types d'events publiés par les agents
    public static final String AGENT_REGISTERED        = "AgentRegistered";
    public static final String AGENT_HEARTBEAT         = "AgentHeartbeat";
    public static final String TASK_CLAIMED            = "TaskClaimedByAgent";
    public static final String TASK_WORK_IN_PROGRESS   = "TaskWorkInProgress";
    public static final String TASK_COMPLETED          = "TaskCompleted";
    public static final String TASK_FAILED             = "TaskFailed";
}
```

### AgentSignal

```java
public sealed interface AgentSignal
        permits ScenarioRestartSignal {
    SignalId id();
    Instant issuedAt();
}
```

### PartialExecutionContext

```java
/**
 * Sous-ensemble de l'ExecutionContext global.
 * Contient uniquement les entrées déclarées dans StepDefinition.requiredContexts().
 * Structure du store : taskId → Map<AgentId, TaskResult>
 */
public record PartialExecutionContext(
    ExecutionId executionId,
    ScenarioId scenarioId,
    Map<String, Map<String, Object>> store   // taskId → (agentId → outputs)
) {
    public <T> Optional<T> get(String taskId, String agentId, Class<T> type) { ... }

    // Accès au premier résultat disponible pour une task (politique FIRST_COMPLETE)
    public <T> Optional<T> getFirst(String taskId, Class<T> type) { ... }
}
```

---

## 4. Implémentations

### 4.1 Kafka Transport

```yaml
transport:
  type: KAFKA
  kafka:
    bootstrapServers: kafka:9092
    # Topic unique pour les demandes de task (broadcast)
    # Chaque agent a son propre consumer group → reçoit tous les messages → filtre local
    # Voir ADR-009 pour la justification du consumer group par agent
    tasksTopic: agents-tasks
    eventsTopic: agents-events
    signalsTopic: agents-signals
    producerAcks: all
```

**Consumer group côté agent** (config de l'agent, pas de l'orchestrateur) :

```yaml
agent:
  transport:
    kafka:
      # Consumer group unique par agent — OBLIGATOIRE pour recevoir tous les messages.
      # Si plusieurs agents partagent le même group, Kafka distribue les messages
      # entre eux (load balancing) au lieu de les broadcaster.
      # Règle : consumerGroup = agent.id (auto-configuré si absent)
      consumerGroup: ${agent.id}
```

**Topics** :

| Topic | Producteur | Consommateurs | Partitions recommandées |
|---|---|---|---|
| `agents-tasks` | Orchestrateur | Tous les agents (consumer group distinct par agent) | 1 (ordering) |
| `agents-events` | Tous les agents | Orchestrateur | N (parallélisme) |
| `agents-signals` | Orchestrateur | Tous les agents | 1 |

### 4.2 RabbitMQ Transport

```yaml
transport:
  type: RABBITMQ
  rabbitmq:
    host: rabbitmq
    port: 5672
    virtualHost: /performance
    # Exchange FANOUT pour broadcast vers tous les agents
    # Chaque agent crée une queue exclusive auto-delete à son démarrage
    tasksExchange: agents.tasks
    tasksExchangeType: FANOUT          # FANOUT = broadcast natif
    eventsExchange: agents.events
    eventsExchangeType: DIRECT
    signalsExchange: agents.signals
    signalsExchangeType: FANOUT
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
```

### 4.3 HTTP Transport

```yaml
transport:
  type: HTTP
  http:
    # L'orchestrateur envoie la TaskExecutionRequest à tous les agents enregistrés
    # supportant le taskName concerné (broadcast HTTP).
    # broadcastMode:
    #   ALL_CAPABLE  : envoie à tous les agents supportant la task (multi-claim natif)
    #   FIRST_AVAILABLE : envoie au premier agent disponible uniquement
    broadcastMode: ALL_CAPABLE
    requestTimeoutSeconds: 30
    # NOTE R5 : taskAvailabilityTimeoutSeconds doit être supérieur au temps de
    # démarrage d'un agent dans l'environnement cible.
    # En K8s avec cold start, prévoir minimum 120s.
    # En dev local, 30s est suffisant.
    taskAvailabilityTimeoutSeconds: 120
    # Endpoint de l'orchestrateur où les agents envoient leurs callbacks
    callbackBasePath: /api/v1/events
```

### 4.4 Socket Transport

```yaml
transport:
  type: SOCKET
  socket:
    orchestratorHost: orchestrator
    orchestratorPort: 9000
    backlog: 100
    keepAlive: true
    reconnectIntervalMs: 5000
    # Broadcast implémenté côté orchestrateur : envoi sur toutes les connexions actives
```

### 4.5 InMemory Transport (tests + mode LOCAL)

```yaml
transport:
  type: IN_MEMORY
  # Utilisé automatiquement en mode LOCAL
  # Queues en mémoire, synchrone ou asynchrone selon config
  inMemory:
    asyncDelivery: false    # false = synchrone pour les tests déterministes
```

---

## 5. Sélection par Configuration

```java
@Configuration
public class TransportConfiguration {

    @Bean
    @ConditionalOnProperty(name = "transport.type", havingValue = "KAFKA")
    public ExecutionTransport kafkaTransport(KafkaTransportProperties props) {
        return new KafkaExecutionTransport(props);
    }

    @Bean
    @ConditionalOnProperty(name = "transport.type", havingValue = "RABBITMQ")
    public ExecutionTransport rabbitTransport(RabbitMQTransportProperties props) {
        return new RabbitMQExecutionTransport(props);
    }

    @Bean
    @ConditionalOnProperty(name = "transport.type", havingValue = "HTTP")
    public ExecutionTransport httpTransport(HttpTransportProperties props,
                                            AgentRegistry registry) {
        return new HttpExecutionTransport(props, registry);
    }

    @Bean
    @ConditionalOnProperty(name = "transport.type", havingValue = "SOCKET")
    public ExecutionTransport socketTransport(SocketTransportProperties props) {
        return new SocketExecutionTransport(props);
    }

    @Bean
    @ConditionalOnProperty(name = "transport.type",
                           havingValue = "IN_MEMORY",
                           matchIfMissing = false)
    @ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
    public ExecutionTransport inMemoryTransport() {
        return new InMemoryExecutionTransport();
    }
}
```

---

## 6. Garanties de Livraison

| Transport | Modèle | At-least-once | Ordering tasks | Idempotence requise |
|---|---|---|---|---|
| IN_MEMORY | Synchrone | Oui | Garanti | Non (single thread) |
| SOCKET | Broadcast connexions | Non (best-effort) | Par connexion | Oui |
| RABBITMQ | FANOUT + ack | Oui | Par queue | Oui |
| KAFKA | Broadcast par consumer group | Oui (offset) | Par partition | Oui |
| HTTP | POST direct + retry | Oui (retry orchestrateur) | N/A | Oui |

Clé d'idempotence côté agent : `TaskExecutionRequest.id` (MessageId).
Un agent ne doit pas ré-exécuter une request avec un `MessageId` déjà traité.

---

## 7. Flux de Registration via Transport

L'enregistrement des agents passe par le transport pour rester cohérent
quel que soit le mécanisme de communication.

```
Agent démarre
  → transport.connect()
  → publie ExecutionEvent(eventType=AgentRegistered, payload=AgentDescriptor)
  → Orchestrateur reçoit via subscribe() → AgentRegistry.onAgentRegistered()

Agent heartbeat (toutes les heartbeat.intervalSeconds)
  → publie ExecutionEvent(eventType=AgentHeartbeat, payload=AgentHeartbeat)
  → Orchestrateur → AgentRegistry.onAgentHeartbeat() → refresh TTL

Agent s'arrête (graceful)
  → publie ExecutionEvent(eventType=AgentDeregistered)
  → Orchestrateur → AgentRegistry.onAgentDeregistered()

TTL expiré (pas de heartbeat pendant registrationTtl)
  → Orchestrateur → AgentRegistry.onAgentExpired()
  → Orchestrateur publie ScenarioRestartSignal si exécution en cours sur cet agent
```

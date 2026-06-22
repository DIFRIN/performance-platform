# PDR-008 — Transport Implementations (Kafka / RabbitMQ / HTTP / Socket)

**Module Maven** : `platform-transport`
**Package** : `com.performance.platform.transport.{kafka,rabbitmq,http,socket}`
**Statut** : WAITING
**Specs de référence** : `.claude/knowledge/specs/05-transport-layer.md` §4-6, ADR-002, ADR-009
**Dépend de** : PDR-001, PDR-002, PDR-007
**Issues** : ISSUE-028, ISSUE-029, ISSUE-030, ISSUE-031, ISSUE-032

---

## Responsabilité

Fournit les quatre implémentations concrètes de `ExecutionTransport` sélectionnées par
`@ConditionalOnProperty(name="transport.type")` : Kafka, RabbitMQ, HTTP, Socket. Chacune
respecte le modèle broadcast (Kafka = consumer group par agent, RabbitMQ = FANOUT, HTTP =
broadcast vers agents enregistrés, Socket = broadcast sur connexions actives). Inclut les
`@ConfigurationProperties` de chaque transport et la `TransportConfiguration`.

**gRPC NON implémenté** (décision validée). Pas de `GrpcExecutionTransport`.

---

## Interfaces Publiques

```java
@Component
@ConditionalOnProperty(name = "transport.type", havingValue = "KAFKA")
public class KafkaExecutionTransport implements ExecutionTransport {
    public KafkaExecutionTransport(KafkaTransportProperties props) { /* ... */ }
}

@Component
@ConditionalOnProperty(name = "transport.type", havingValue = "RABBITMQ")
public class RabbitMQExecutionTransport implements ExecutionTransport {
    public RabbitMQExecutionTransport(RabbitMQTransportProperties props) { /* ... */ }
}

@Component
@ConditionalOnProperty(name = "transport.type", havingValue = "HTTP")
public class HttpExecutionTransport implements ExecutionTransport {
    public HttpExecutionTransport(HttpTransportProperties props, AgentRegistryPort registry) { /* ... */ }
}

@Component
@ConditionalOnProperty(name = "transport.type", havingValue = "SOCKET")
public class SocketExecutionTransport implements ExecutionTransport {
    public SocketExecutionTransport(SocketTransportProperties props) { /* ... */ }
}

@Configuration
public class TransportConfiguration { /* @Bean conditionnels */ }
```

### Properties

```java
@ConfigurationProperties(prefix = "transport.kafka")
public record KafkaTransportProperties(
    String bootstrapServers, String tasksTopic, String eventsTopic,
    String signalsTopic, String producerAcks
) {}

@ConfigurationProperties(prefix = "transport.rabbitmq")
public record RabbitMQTransportProperties(
    String host, int port, String virtualHost,
    String tasksExchange, String eventsExchange, String signalsExchange,
    String username, String password
) {}

@ConfigurationProperties(prefix = "transport.http")
public record HttpTransportProperties(
    String broadcastMode, int requestTimeoutSeconds,
    int taskAvailabilityTimeoutSeconds, String callbackBasePath
) {}

@ConfigurationProperties(prefix = "transport.socket")
public record SocketTransportProperties(
    String orchestratorHost, int orchestratorPort, int backlog,
    boolean keepAlive, int reconnectIntervalMs
) {}
```

---

## Règles de Comportement

- Aucun `if (type == KAFKA)` dans le code métier : sélection uniquement par `@ConditionalOnProperty` (CF-03).
- Kafka : consumer group = `agent.id` (ADR-009), topic `agents-tasks` 1 partition (ordering).
- RabbitMQ : exchange FANOUT pour tasks/signals, DIRECT pour events ; queue exclusive auto-delete par agent.
- HTTP : `broadcastMode=ALL_CAPABLE` → envoi à tous les agents supportant le taskName ; callbacks via `AgentRegistryPort`.
- Socket : broadcast sur toutes les connexions actives, reconnexion best-effort.
- Sérialisation : Jackson (events/requests) — réutiliser un `ObjectMapper` configuré.
- Tout I/O bloquant → Virtual Threads.
- Chaque transport implémente `getType()` retournant la bonne valeur `TransportType`.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → TransportType, MessageId, ...
  PDR-002 → AgentSignal
  PDR-007 → ExecutionTransport, TaskExecutionRequest, ExecutionEvent, handlers
  PDR-004 → AgentRegistryPort (pour HTTP transport)

Ce PDR est utilisé par :
  PDR-018 (platform-app) → assemblage selon TRANSPORT_TYPE
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Tests d'intégration Testcontainers : Kafka + RabbitMQ round-trip
- [ ] Aucun branchement conditionnel sur le type dans le code métier (code review)
- [ ] Implémentations dans `.claude/context/interfaces-registry.md` STABLE

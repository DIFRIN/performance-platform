# ISSUE-030 — RabbitMQExecutionTransport (FANOUT)

**PDR** : PDR-008
**Module** : `platform-transport`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-027, ISSUE-028
**Estime** : L

---

## Objectif

Implémenter `RabbitMQExecutionTransport` : broadcast via exchange FANOUT, queue exclusive
auto-delete par agent. Sérialisation Jackson.

## Fichiers à Créer

```
platform-transport/src/main/java/com/performance/platform/transport/rabbitmq/
  └── RabbitMQExecutionTransport.java

platform-transport/src/test/java/com/performance/platform/transport/rabbitmq/
  └── RabbitMQExecutionTransportIT.java   — Testcontainers RabbitMQ, round-trip
```

## Interfaces à Implémenter

```java
@Component
@ConditionalOnProperty(name = "transport.type", havingValue = "RABBITMQ")
public class RabbitMQExecutionTransport implements ExecutionTransport {
    public RabbitMQExecutionTransport(RabbitMQTransportProperties props) { /* ... */ }
    public TransportType getType() { return TransportType.RABBITMQ; }
}
```

## Règles Spécifiques

- Exchange FANOUT pour tasks/signals (broadcast natif), DIRECT pour events.
- Chaque agent crée une queue exclusive auto-delete à son démarrage.
- ack manuel (at-least-once). I/O sous Virtual Threads.

## Critères de Done

- [ ] `mvn verify -pl platform-transport -P integration-tests` → IT RabbitMQ passe
- [ ] Round-trip via Testcontainers RabbitMQ
- [ ] `getType()` == RABBITMQ
- [ ] `progress.md` mis à jour : ISSUE-030 → DONE
- [ ] `context/interfaces-registry.md` : `RabbitMQExecutionTransport` → STABLE

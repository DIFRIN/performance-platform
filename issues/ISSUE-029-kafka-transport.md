# ISSUE-029 — KafkaExecutionTransport (consumer group par agent)

**PDR** : PDR-008
**Module** : `platform-transport`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-027, ISSUE-028
**Estime** : L

---

## Objectif

Implémenter `KafkaExecutionTransport` : broadcast via topic + consumer group unique par agent
(ADR-009). Sérialisation Jackson.

## Fichiers à Créer

```
platform-transport/src/main/java/com/performance/platform/transport/kafka/
  ├── KafkaExecutionTransport.java
  └── KafkaMessageCodec.java   — (de)sérialisation JSON

platform-transport/src/test/java/com/performance/platform/transport/kafka/
  └── KafkaExecutionTransportIT.java   — Testcontainers Kafka, round-trip
```

## Interfaces à Implémenter

```java
@Component
@ConditionalOnProperty(name = "transport.type", havingValue = "KAFKA")
public class KafkaExecutionTransport implements ExecutionTransport {
    public KafkaExecutionTransport(KafkaTransportProperties props) { /* ... */ }
    public TransportType getType() { return TransportType.KAFKA; }
}
```

## Règles Spécifiques

- Topics : `agents-tasks` (1 partition, ordering), `agents-events` (N partitions), `agents-signals` (1).
- Consumer group côté agent = `agent.id` (ADR-009) — chaque agent reçoit TOUS les messages.
- Producer acks=all.
- I/O sous Virtual Threads.

## Critères de Done

- [ ] `mvn verify -pl platform-transport -P integration-tests` → IT Kafka passe
- [ ] Round-trip dispatchTask → receiveTask via Testcontainers Kafka
- [ ] `getType()` == KAFKA
- [ ] `progress.md` mis à jour : ISSUE-029 → DONE
- [ ] `context/interfaces-registry.md` : `KafkaExecutionTransport` → STABLE

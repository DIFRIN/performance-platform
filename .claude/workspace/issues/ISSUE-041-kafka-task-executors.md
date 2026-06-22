# ISSUE-041 — KafkaConsumerTaskExecutor + KafkaProducerTaskExecutor

**PDR** : PDR-010
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-039
**Estime** : L

---

## Objectif

Implémenter les executors Kafka de préparation : consumer (MONITOR/CONSUME/COUNT) et producer
(PRELOAD/PRODUCE).

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/kafka/
  ├── KafkaConsumerTaskExecutor.java
  └── KafkaProducerTaskExecutor.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/kafka/
  └── KafkaTaskExecutorsIT.java — Testcontainers Kafka
```

## Interfaces à Implémenter

```java
@Preparation(name = "kafka-consumer", description = "Kafka consume/monitor/count")
public class KafkaConsumerTaskExecutor implements TaskExecutor {
    public String getSupportedTaskName() { return "kafka-consumer"; }
    public TaskResult execute(ExecutionContext context, StepDefinition step) { /* ... */ }
}

@Preparation(name = "kafka-producer", description = "Kafka preload/produce")
public class KafkaProducerTaskExecutor implements TaskExecutor {
    public String getSupportedTaskName() { return "kafka-producer"; }
    public TaskResult execute(ExecutionContext context, StepDefinition step) { /* ... */ }
}
```

## Règles Spécifiques

- Consumer params : `operation`, `topic`, `groupId`, `bootstrapServers`, `maxMessages`, `timeout`. Outputs : `{ "messagesConsumed": N, "lag": M }`.
- Producer params : `topic`, `bootstrapServers`, `messageCount`, `messageTemplate`, `batchSize`.
- Échec → `TaskResult.failed`. `String taskName` partout.
- Implémentent `StatefulResourceCleaner` (consumer : commit offsets + close ; producer : flush + close).
- I/O sous Virtual Threads.

## Critères de Done

- [ ] `mvn verify -pl platform-infrastructure -P integration-tests` → IT passe
- [ ] Consumer COUNT retourne `messagesConsumed`
- [ ] Producer PRELOAD produit le bon nombre de messages
- [ ] `.claude/progress.md` mis à jour : ISSUE-041 → DONE
- [ ] `.claude/context/interfaces-registry.md` : executors Kafka → STABLE

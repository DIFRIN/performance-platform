# ISSUE-088 — Refactor KafkaConsumerTaskExecutor → Spring ConsumerFactory + cluster reference

**PDR** : PDR-020
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-086 (KafkaClusterRegistry doit être DONE)
**Estime** : M

---

## Objectif

Refactoriser `KafkaConsumerTaskExecutor` pour supprimer tout code raw Kafka (`new Properties()`, `new KafkaConsumer(...)`) et utiliser :
1. `KafkaClusterRegistry.consumerFactory(clusterName, groupId)` → `ConsumerFactory<String,String>` Spring
2. Créer un `KafkaConsumer` via `consumerFactory.createConsumer()` (géré par Spring, pas manuellement)
3. Logique hybride : `cluster:` référence le registry, `bootstrapServers:` legacy avec log WARN

Le comportement externe (CONSUME, COUNT, outputs `messagesConsumed`, `lag`) reste identique.

---

## Fichiers à Modifier

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/kafka/
  └── KafkaConsumerTaskExecutor.java     — refactoring

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/kafka/
  └── KafkaConsumerTaskExecutorTest.java — mise à jour (mock KafkaClusterRegistry)
```

---

## Interfaces à Implémenter

```java
@Preparation(name = "kafka-consumer", version = "2.0.0",
        description = "Kafka consumer via named cluster reference or inline bootstrap servers")
@Component
public class KafkaConsumerTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerTaskExecutor.class);

    private final KafkaClusterRegistry clusterRegistry;

    // Map<executionKey, Consumer> pour cleanup — identique à avant
    private final Map<String, org.apache.kafka.clients.consumer.Consumer<String, String>>
            consumersByExecution = new ConcurrentHashMap<>();

    public KafkaConsumerTaskExecutor(KafkaClusterRegistry clusterRegistry) {
        this.clusterRegistry = Objects.requireNonNull(clusterRegistry);
    }

    @Override
    public String getSupportedTaskName() { return "kafka-consumer"; }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        // 1. Extraire params
        String clusterName      = (String) step.parameters().get("cluster");
        String bootstrapServers = (String) step.parameters().get("bootstrapServers"); // legacy
        String logicalTopic     = (String) step.parameters().get("topic");
        String groupId          = Objects.toString(step.parameters().get("groupId"), "perf-consumer-group");

        // 2. Résoudre topic et créer ConsumerFactory
        //    - cluster: → registry.consumerFactory(clusterName, groupId) → .createConsumer()
        //    - bootstrapServers: → factory éphémère inline (DefaultKafkaConsumerFactory)
        //    - aucun des deux → TaskResult.failed(...)

        // 3. Consumer obtenu via factory.createConsumer() — pas new KafkaConsumer(Properties)
        // 4. Suite identique : CONSUME poll loop, COUNT via offsets
        // 5. cleanup : consumersByExecution.get(key).wakeup() + close()
    }

    // executeConsume, executeCount, pollMessages, computeLag, sumPartitionOffsets
    // → logique identique, Consumer<String,String> vient du factory Spring
}
```

**Paramètres DSL step** :
```yaml
# Nouveau (avec registry)
parameters:
  cluster: iot-sut
  topic: iot-commands            # résolu depuis topics map du cluster
  operation: CONSUME
  maxMessages: 500
  groupId: my-consumer-group     # override du consumerGroup du cluster

# Legacy (rétrocompatible, log WARN)
parameters:
  bootstrapServers: "localhost:9092"
  topic: raw-topic
  operation: COUNT
```

---

## Règles Spécifiques

- **`groupId` override** : si `groupId` fourni dans step params, il surpasse le `consumerGroup` du cluster. Permet des consumer groups ad-hoc par step.
- **Factory Spring vs Consumer raw** : `DefaultKafkaConsumerFactory.createConsumer()` retourne un `org.apache.kafka.clients.consumer.Consumer<K,V>` — même interface que `KafkaConsumer`. Toute la logique poll/commit/lag reste identique.
- **Cleanup** : `cleanup(ExecutionId)` wakeup + close identique — l'interface `Consumer` supporte `wakeup()` et `close()`.
- **Tests** : `MockConsumer<String, String>` de Kafka test utils pour simuler poll sans Kafka réel.

---

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Aucun `new Properties()` + `new KafkaConsumer(...)` dans `KafkaConsumerTaskExecutor`
- [ ] Test : CONSUME avec `cluster: default` → registry consulté, topic résolu, factory créé
- [ ] Test : COUNT avec `bootstrapServers:` legacy → WARN loggé
- [ ] Test : topic logique résolu depuis topics map du cluster
- [ ] Test : cleanup wakeup + close via `Consumer` interface
- [ ] `.claude/progress.md` mis à jour : ISSUE-088 → DONE

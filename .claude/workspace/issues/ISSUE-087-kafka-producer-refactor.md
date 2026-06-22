# ISSUE-087 — Refactor KafkaProducerTaskExecutor → Spring KafkaTemplate + cluster reference

**PDR** : PDR-020
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-086 (KafkaClusterRegistry doit être DONE)
**Estime** : M

---

## Objectif

Refactoriser `KafkaProducerTaskExecutor` pour supprimer tout code raw Kafka (`new Properties()`, `new KafkaProducer(...)`) et utiliser :
1. `KafkaClusterRegistry` (injecté par Spring) pour résoudre le cluster et le nom réel du topic
2. `KafkaTemplate<String, String>` créé depuis `KafkaClusterRegistry.producerFactory(clusterName)`
3. Logique hybride : si `cluster:` présent dans les params du step → utiliser le registry. Si seulement `bootstrapServers:` → créer un factory éphémère (rétrocompatibilité, log WARN).

Le comportement externe (outputs `messagesProduced`, `messagesFailed`) reste identique. Les tests existants doivent passer.

---

## Fichiers à Modifier

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/kafka/
  └── KafkaProducerTaskExecutor.java    — refactoring complet

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/kafka/
  └── KafkaProducerTaskExecutorTest.java — mise à jour des tests (mock KafkaClusterRegistry)
```

---

## Interfaces à Implémenter

```java
// KafkaProducerTaskExecutor.java — classe entière refactorisée
@Preparation(name = "kafka-producer", version = "2.0.0",
        description = "Kafka produce via named cluster reference or inline bootstrap servers")
@Component
public class KafkaProducerTaskExecutor implements TaskExecutor, StatefulResourceCleaner {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerTaskExecutor.class);

    // Injecté par Spring — null-safe si aucun cluster configuré
    private final KafkaClusterRegistry clusterRegistry;

    public KafkaProducerTaskExecutor(KafkaClusterRegistry clusterRegistry) {
        this.clusterRegistry = Objects.requireNonNull(clusterRegistry);
    }

    @Override
    public String getSupportedTaskName() { return "kafka-producer"; }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        // 1. Extraire params du step
        String clusterName      = (String) step.parameters().get("cluster");
        String bootstrapServers = (String) step.parameters().get("bootstrapServers"); // legacy
        String logicalTopic     = (String) step.parameters().get("topic");

        // 2. Résoudre topic et factory
        //    - si cluster: → registry.resolveTopic() + registry.producerFactory()
        //    - si bootstrapServers: (legacy) → log WARN + factory éphémère inline
        //    - si aucun des deux → TaskResult.failed(...)

        // 3. Créer KafkaTemplate<String,String> depuis factory
        // 4. Appeler executeProduce (logique inchangée, mais via KafkaTemplate.send())
        // 5. cleanup : KafkaTemplate.getProducerFactory().reset() ou destroy()
    }

    // Méthodes sendMessages, resolveTemplate, parseMessageCount, parseBatchSize
    // → logique identique, adaptée pour utiliser KafkaTemplate au lieu de KafkaProducer raw
}
```

**Paramètres DSL step (nouveaux)** :
```yaml
# Nouveau (avec registry)
parameters:
  cluster: iot-sut               # → KafkaClusterRegistry.get("iot-sut")
  topic: iot-commands            # → KafkaClusterRegistry.resolveTopic("iot-sut", "iot-commands")
  messageCount: 1000
  messageTemplate: '{"device_id":"device-{index}"}'

# Legacy (rétrocompatible, log WARN)
parameters:
  bootstrapServers: "localhost:9092"
  topic: my-topic
  messageCount: 100
```

---

## Règles Spécifiques

- **Priorité** : `cluster:` > `bootstrapServers:` (inline legacy). Si les deux sont fournis, utiliser `cluster:`.
- **Fallback topic** : si `cluster:` fourni mais topic non mappé dans registry → utiliser le nom logique tel quel (comportement `resolveTopic` fallback).
- **`KafkaTemplate.send()`** : utiliser `send(topic, key, value)` et `.get()` pour attendre l'ack (Virtual Thread).
- **Cleanup** : `cleanup(ExecutionId)` appelle `kafkaTemplate.getProducerFactory().reset()` pour fermer les connexions de l'execution donnée. Utiliser `Map<String, KafkaTemplate<String,String>> templatesByExecution` si plusieurs steps concurrents.
- **Version** : passer `version = "2.0.0"` dans `@Preparation` pour signaler le changement.
- **Tests** : mocker `KafkaClusterRegistry` avec `Mockito.mock()`. Utiliser `MockProducer<String,String>` de Kafka test utils pour éviter Testcontainers dans les tests unitaires.

---

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Aucun `new Properties()` dans `KafkaProducerTaskExecutor`
- [ ] Aucun `new KafkaProducer(...)` dans `KafkaProducerTaskExecutor`
- [ ] Test : step avec `cluster: iot-sut` → registry consulté, topic résolu
- [ ] Test : step avec `bootstrapServers:` (legacy) → log WARN émis, factory éphémère créé
- [ ] Test : step sans `cluster:` ni `bootstrapServers:` → `TaskResult.failed(...)` retourné
- [ ] Tests existants (messageCount, batchSize, template, cleanup) toujours verts
- [ ] `.claude/progress.md` mis à jour : ISSUE-087 → DONE

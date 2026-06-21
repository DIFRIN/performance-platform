# ISSUE-086 — KafkaClusterRegistry + KafkaClusterProperties + KafkaClusterConfiguration

**PDR** : PDR-020
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : aucune
**Estime** : M

---

## Objectif

Créer l'infrastructure de configuration nommée pour les clusters Kafka, en suivant exactement le pattern `DatasourceProvider` / `PlatformDatasourcesProperties` / `DatasourceConfiguration` (ADR-014).

Le Developer produit : `KafkaClusterProperties`, `PlatformKafkaProperties`, `KafkaClusterRegistry`, `KafkaClusterConfiguration` + mise à jour de `application-orchestrator.yaml` avec un bloc `platform.kafka-clusters.*` d'exemple.

À la fin de cette Issue, un test prouve qu'on peut : (1) binder des clusters depuis un yaml, (2) résoudre un topic logique, (3) obtenir un `ProducerFactory` / `ConsumerFactory` Spring valide.

---

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/kafka/
  ├── KafkaClusterProperties.java       — record + Map<String,String> topics
  ├── PlatformKafkaProperties.java      — @ConfigurationProperties(prefix="platform")
  ├── KafkaClusterRegistry.java         — registre + resolveTopic() + factory methods
  └── KafkaClusterConfiguration.java    — @Configuration @EnableConfigurationProperties

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/kafka/
  └── KafkaClusterRegistryTest.java     — tests unitaires (pas de Kafka réel)

platform-app/src/main/resources/application-orchestrator.yaml  — ajouter bloc platform.kafka-clusters.*
platform-app/src/main/resources/application-agent.yaml         — ajouter bloc platform.kafka-clusters.* (si pertinent)
```

---

## Interfaces à Implémenter

```java
// KafkaClusterProperties.java
public record KafkaClusterProperties(
    String bootstrapServers,
    String producerAcks,
    String consumerGroup,
    Map<String, String> topics
) {
    public KafkaClusterProperties {
        Objects.requireNonNull(bootstrapServers, "bootstrapServers required");
        producerAcks  = producerAcks  != null ? producerAcks  : "all";
        consumerGroup = consumerGroup != null ? consumerGroup : "perf-consumer";
        topics        = topics        != null ? Map.copyOf(topics) : Map.of();
    }
}

// PlatformKafkaProperties.java
@ConfigurationProperties(prefix = "platform")
public record PlatformKafkaProperties(
    @DefaultValue Map<String, KafkaClusterProperties> kafkaClusters
) {
    public PlatformKafkaProperties {
        kafkaClusters = kafkaClusters != null ? Map.copyOf(kafkaClusters) : Map.of();
    }
}

// KafkaClusterRegistry.java — PAS un @Component, instancié par KafkaClusterConfiguration
public class KafkaClusterRegistry {

    private final Map<String, KafkaClusterProperties> clusters;

    public KafkaClusterRegistry(Map<String, KafkaClusterProperties> clusters) {
        this.clusters = Map.copyOf(clusters);
    }

    /** Retourne les propriétés du cluster ou null si inconnu. */
    public KafkaClusterProperties get(String clusterName) { ... }

    /**
     * Résout un nom logique de topic vers le nom réel.
     * Fallback : retourne logicalTopicName si aucun mapping dans topics map.
     */
    public String resolveTopic(String clusterName, String logicalTopicName) { ... }

    /**
     * Crée un ProducerFactory<String,String> Spring pour ce cluster.
     * Config : STRING_SERIALIZER, acks depuis clusterProps.producerAcks().
     */
    public ProducerFactory<String, String> producerFactory(String clusterName) { ... }

    /**
     * Crée un ConsumerFactory<String,String> Spring pour ce cluster.
     * Config : STRING_DESERIALIZER, auto-commit=false, groupId fourni en param.
     */
    public ConsumerFactory<String, String> consumerFactory(String clusterName, String groupId) { ... }
}

// KafkaClusterConfiguration.java
@Configuration
@EnableConfigurationProperties(PlatformKafkaProperties.class)
public class KafkaClusterConfiguration {

    @Bean
    public KafkaClusterRegistry kafkaClusterRegistry(PlatformKafkaProperties props) {
        return new KafkaClusterRegistry(props.kafkaClusters());
    }
}
```

**Ajout `application-orchestrator.yaml`** :
```yaml
platform:
  # ... (datasources existant)
  kafka-clusters:
    default:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
      producer-acks: all
      consumer-group: perf-consumer
      topics: {}                          # noms réels = noms logiques par défaut
    iot-sut:
      bootstrap-servers: ${IOT_KAFKA_SERVERS:localhost:9093}
      producer-acks: "1"
      consumer-group: perf-test-consumer
      topics:
        iot-commands: ${IOT_TOPIC_COMMANDS:iot-commands}
        device-events: ${DEVICE_TOPIC_EVENTS:device-events}
```

---

## Règles Spécifiques

- `PlatformKafkaProperties` utilise `kafkaClusters` (camelCase) → Spring boot binding kebab `kafka-clusters` automatiquement.
- `resolveTopic` : `clusters.get(clusterName)` → si null → retourner `logicalTopicName`. Si cluster trouvé mais `topics.get(logical)` null → retourner `logicalTopicName`.
- `producerFactory` : utilise `DefaultKafkaProducerFactory<>(Map.of(...))` Spring. Serializers : `StringSerializer`.
- `consumerFactory` : utilise `DefaultKafkaConsumerFactory<>(Map.of(...))` Spring. Deserializers : `StringDeserializer`, `AUTO_OFFSET_RESET=earliest`, `ENABLE_AUTO_COMMIT=false`.
- Pas de `spring-kafka` à ajouter dans le pom.xml : vérifier d'abord s'il est déjà transitivement présent. Si non, l'ajouter sans version (géré par Spring Boot BOM).

---

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Test : binding depuis yaml → `KafkaClusterRegistry.get("iot-sut")` → non null
- [ ] Test : `resolveTopic("iot-sut", "iot-commands")` → `"iot-commands-dev"` (quand mappé)
- [ ] Test : `resolveTopic("iot-sut", "raw-topic")` → `"raw-topic"` (fallback)
- [ ] Test : `resolveTopic("unknown", "anything")` → `"anything"` (fallback sur cluster inconnu)
- [ ] Test : `producerFactory("default")` → `ProducerFactory` non null
- [ ] `application-orchestrator.yaml` contient `platform.kafka-clusters.*` block
- [ ] `.claude/progress.md` mis à jour : ISSUE-086 → DONE
- [ ] `.claude/context/interfaces-registry.md` mis à jour : KafkaClusterRegistry → IN PROGRESS

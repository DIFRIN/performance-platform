# PDR-020 — Kafka Cluster Registry + Spring Kafka Executors

**Module Maven** : `platform-infrastructure`
**Package** : `com.performance.platform.infrastructure.executor.kafka`
**Statut** : WAITING
**Specs de référence** : `.claude/specifications/03-task-framework.md`, `.claude/adr/ADR-013-spring-first-infrastructure.md`, `.claude/adr/ADR-014-datasource-configuration.md`
**Dépend de** : PDR-010 (DONE — DatasourceProvider pattern à répliquer)
**Issues** : ISSUE-086, ISSUE-087, ISSUE-088

---

## Responsabilité

Introduit un registre nommé de clusters Kafka (`KafkaClusterRegistry`) calqué exactement sur le pattern `DatasourceProvider` / `PlatformDatasourcesProperties` (ADR-014). Chaque cluster est défini dans `application-*.yaml` sous `platform.kafka-clusters.<id>.*` et référencé dans le scénario DSL uniquement par son identifiant logique.

Ajoute la résolution de noms logiques de topics : `platform.kafka-clusters.<id>.topics.<logical-name>` → nom réel. Permet au même `scenario.yaml` d'être utilisé sur dev, staging, prod sans modification (seul `application-*.yaml` change).

Refactorise `KafkaProducerTaskExecutor` et `KafkaConsumerTaskExecutor` :
- Remplace `new Properties()` + `new KafkaProducer/Consumer(...)` par Spring `KafkaTemplate` / `DefaultKafkaConsumerFactory`
- Lit la config depuis le cluster nommé (pas depuis des params inline)
- Permet un override de `bootstrapServers` par step (hybride) si le cluster n'est pas déclaré dans la config

**Ne fait PAS** : ne touche pas au transport interne orchestrateur↔agents (`transport.kafka.*` reste séparé — PDR-021).

---

## Interfaces Publiques

```java
// ---- Properties (binding Spring @ConfigurationProperties) ----

/**
 * Propriétés d'un cluster Kafka nommé.
 * Déclarées sous platform.kafka-clusters.<id>.*
 */
public record KafkaClusterProperties(
    String bootstrapServers,           // required
    String producerAcks,               // default "all"
    String consumerGroup,              // default "perf-consumer"
    Map<String, String> topics         // logical-name → actual-topic-name (env-specific)
) {
    public KafkaClusterProperties {
        Objects.requireNonNull(bootstrapServers, "bootstrapServers required");
        producerAcks  = producerAcks  != null ? producerAcks  : "all";
        consumerGroup = consumerGroup != null ? consumerGroup : "perf-consumer";
        topics        = topics        != null ? Map.copyOf(topics) : Map.of();
    }
}

/**
 * Binding racine. Clé : platform.kafka-clusters.*
 * Exemple application.yaml :
 *
 *   platform:
 *     kafka-clusters:
 *       default:
 *         bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
 *         producer-acks: all
 *         consumer-group: perf-consumer
 *         topics:
 *           iot-commands: iot-commands-dev
 *           device-events: device-events-dev-v2
 *       iot-sut:
 *         bootstrap-servers: ${IOT_KAFKA_SERVERS:localhost:9093}
 *         topics:
 *           iot-commands: iot-commands-prod-v3
 */
@ConfigurationProperties(prefix = "platform")
public record PlatformKafkaProperties(
    Map<String, KafkaClusterProperties> kafkaClusters   // jamais null (Map.of() si absent)
) {
    public PlatformKafkaProperties {
        kafkaClusters = kafkaClusters != null ? Map.copyOf(kafkaClusters) : Map.of();
    }
}

// ---- Registry ----

/**
 * Registre thread-safe des clusters Kafka nommés.
 * Instancié par KafkaClusterConfiguration (Spring @Bean).
 * Pattern identique à DatasourceProvider (ADR-014).
 */
public class KafkaClusterRegistry {

    /**
     * Retourne les propriétés du cluster nommé, ou null si inconnu.
     */
    public KafkaClusterProperties get(String clusterName);

    /**
     * Résout un nom logique de topic en nom réel pour un cluster donné.
     * Fallback : retourne logicalTopicName tel quel si aucun mapping trouvé.
     *
     * Exemple :
     *   resolveTopic("iot-sut", "iot-commands") → "iot-commands-dev" (depuis topics map)
     *   resolveTopic("iot-sut", "raw-topic")    → "raw-topic"        (fallback, aucun mapping)
     */
    public String resolveTopic(String clusterName, String logicalTopicName);

    /**
     * Construit un ProducerFactory<String,String> Spring pour le cluster nommé.
     * Utilisé par les executors pour créer un KafkaTemplate éphémère.
     */
    public ProducerFactory<String, String> producerFactory(String clusterName);

    /**
     * Construit un ConsumerFactory<String,String> Spring pour le cluster nommé.
     */
    public ConsumerFactory<String, String> consumerFactory(String clusterName, String groupId);
}

// ---- Configuration Spring ----

/**
 * Crée et expose KafkaClusterRegistry comme @Bean.
 * Conditionnel : actif si platform.kafka-clusters.* est défini.
 */
@Configuration
@EnableConfigurationProperties(PlatformKafkaProperties.class)
public class KafkaClusterConfiguration {

    @Bean
    public KafkaClusterRegistry kafkaClusterRegistry(PlatformKafkaProperties props);
}
```

---

## Règles de Comportement

- **Résolution topic** : `resolveTopic(cluster, logical)` cherche dans `topics` map du cluster. Si absent → retourne `logical` (fallback transparent, rétrocompatible).
- **Override inline** : si un step fournit `bootstrapServers` directement (pas de `cluster:`), les executors créent un factory éphémère avec ces valeurs. Le registry n'est pas consulté.
- **Priority** : `cluster:` param dans le step > override inline `bootstrapServers:` > exception si aucun des deux.
- **Immutabilité** : `KafkaClusterProperties.topics` → `Map.copyOf()` dans le constructeur compact.
- **Thread-safe** : `KafkaClusterRegistry` utilise `Map.copyOf()` à la construction (pas de mutation post-init).
- **Spring Kafka** : les `ProducerFactory` / `ConsumerFactory` créés utilisent `StringSerializer` / `StringDeserializer`. Pas d'auto-commit (ENABLE_AUTO_COMMIT_CONFIG = false).

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-010 (DatasourceProvider)     → pattern identique à répliquer
  spring-kafka                     → ProducerFactory, ConsumerFactory, KafkaTemplate
  platform-infrastructure/pom.xml  → ajouter <dependency>spring-kafka</dependency>

Ce PDR est utilisé par :
  PDR-021 (transport Spring Kafka)  → partage le pattern Spring Kafka
  PDR-024 (scénarios exemples)      → scenarios YAML référencent cluster: iot-sut / topic: iot-commands
```

---

## Critères de Done (PDR complet)

- [ ] ISSUE-086, 087, 088 toutes DONE
- [ ] `KafkaClusterRegistry` + `KafkaClusterProperties` dans interfaces-registry.md avec statut STABLE
- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Aucun `new Properties()`, aucun `new KafkaConsumer(...)`, aucun `new KafkaProducer(...)` dans les task executors

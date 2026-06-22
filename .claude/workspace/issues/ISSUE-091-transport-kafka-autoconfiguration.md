# ISSUE-091 — TransportConfiguration Spring Kafka autoconfiguration

**PDR** : PDR-021
**Module** : `platform-transport`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-090 (DynamicKafkaListenerRegistry doit être DONE)
**Estime** : S

---

## Objectif

Finaliser l'intégration Spring Kafka dans la configuration du transport :
1. Ajouter le `@Bean ConcurrentKafkaListenerContainerFactory<String,byte[]>` (transport interne, qualifié pour éviter collision avec les beans PDR-020)
2. Supprimer définitivement `KafkaConsumerManager` s'il est encore présent (marqué @Deprecated dans ISSUE-090)
3. Vérifier que `TransportConfiguration` câble correctement `KafkaExecutionTransport` avec les nouveaux beans Spring (`KafkaTemplate`, `ConcurrentKafkaListenerContainerFactory`)
4. `mvn verify -pl platform-transport` passe avec tous les tests d'intégration

---

## Fichiers à Modifier

```
platform-transport/src/main/java/com/performance/platform/transport/config/
  ├── KafkaTransportBeans.java    — MODIFIÉ : ajouter ConcurrentKafkaListenerContainerFactory
  └── TransportConfiguration.java — MODIFIÉ : câbler KafkaExecutionTransport avec nouveaux beans

platform-transport/src/main/java/com/performance/platform/transport/kafka/
  └── KafkaConsumerManager.java   — SUPPRIMÉ définitivement

platform-transport/pom.xml        — vérifier spring-kafka présent (ajouté par PDR-020 si même BOM)
```

---

## Interfaces à Implémenter

```java
// KafkaTransportBeans.java — complet après ISSUE-089 + ISSUE-091
@Configuration
@ConditionalOnProperty(name = "transport.type", havingValue = "KAFKA")
public class KafkaTransportBeans {

    // Bean existant depuis ISSUE-089 :
    @Bean("transportProducerFactory")
    public ProducerFactory<String, byte[]> transportProducerFactory(
            KafkaTransportProperties props) { ... }

    @Bean("transportKafkaTemplate")
    public KafkaTemplate<String, byte[]> transportKafkaTemplate(
            @Qualifier("transportProducerFactory")
            ProducerFactory<String, byte[]> factory) { ... }

    // Bean NOUVEAU (ISSUE-091) :
    @Bean("transportConsumerFactory")
    public ConsumerFactory<String, byte[]> transportConsumerFactory(
            KafkaTransportProperties props) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,       props.bootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,  StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,       "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,      false
        );
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean("transportContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, byte[]>
            transportContainerFactory(
                @Qualifier("transportConsumerFactory")
                ConsumerFactory<String, byte[]> transportConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(transportConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    // Bean KafkaExecutionTransport complet (câblé avec tous les beans)
    @Bean
    public KafkaExecutionTransport kafkaExecutionTransport(
            KafkaTransportProperties props,
            @Qualifier("transportKafkaTemplate") KafkaTemplate<String, byte[]> template,
            @Qualifier("transportContainerFactory")
            ConcurrentKafkaListenerContainerFactory<String, byte[]> containerFactory) {
        return new KafkaExecutionTransport(props, template, containerFactory);
    }
}
```

---

## Règles Spécifiques

- **Qualifier obligatoire** sur tous les beans transport Kafka (préfixe `transport`) pour éviter collision avec les beans PDR-020 (`KafkaClusterConfiguration`) et les éventuels beans Spring Boot auto-configurés depuis `spring.kafka.*`.
- **Suppression `KafkaConsumerManager`** : si encore présent, supprimer le fichier. Vérifier qu'aucune autre classe ne l'importe.
- **`platform-transport/pom.xml`** : ajouter `spring-kafka` si pas déjà transitif. Sans version (BOM Spring Boot gère).
- **Test smoke** : un test `@SpringBootTest` minimal avec `@ConditionalOnProperty` désactivé suffit pour vérifier le câblage sans Kafka réel.

---

## Critères de Done

- [ ] `mvn test -pl platform-transport -q` → 0 erreur, 0 warning
- [ ] `KafkaConsumerManager.java` n'existe plus dans le codebase
- [ ] Aucun import de `KafkaConsumerManager` dans le module
- [ ] `KafkaExecutionTransport` reçoit `KafkaTemplate` + `ConcurrentKafkaListenerContainerFactory` par constructeur
- [ ] Tous les beans qualifiés `transport*` pour éviter collision
- [ ] `.claude/progress.md` mis à jour : ISSUE-091 → DONE, PDR-021 → DONE (si 089+090+091 DONE)
- [ ] `.claude/context/interfaces-registry.md` : `DynamicKafkaListenerRegistry` → STABLE, `KafkaConsumerManager` → ❌ REMOVED

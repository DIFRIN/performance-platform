# PDR-021 — Spring Kafka Migration — Transport Layer

**Module Maven** : `platform-transport`
**Package** : `com.performance.platform.transport.kafka`
**Statut** : WAITING
**Specs de référence** : `.claude/adr/ADR-009-kafka-consumer-group-per-agent.md`, `.claude/adr/ADR-013-spring-first-infrastructure.md`, `.claude/specifications/05-transport-layer.md`
**Dépend de** : PDR-020 DONE (spring-kafka déjà intégré au projet, patterns établis)
**Issues** : ISSUE-089, ISSUE-090, ISSUE-091

---

## Responsabilité

Migre `KafkaExecutionTransport` des clients Kafka raw (`KafkaProducer<String,byte[]>`, boucles `KafkaConsumer` manuelles dans `KafkaConsumerManager`) vers Spring Kafka (`KafkaTemplate`, `KafkaMessageListenerContainer` dynamiques).

**Côté produce** : `KafkaTemplate<String, byte[]>` géré par Spring (reconnexion, metrics, tracing automatiques).

**Côté consume** : remplace le `KafkaConsumerManager` (Thread pool manuel, polling loop) par des `KafkaMessageListenerContainer` créés dynamiquement — un container par agent qui se connecte (ADR-009 : consumer group = agentId). Les containers sont démarrés/arrêtés à la demande.

**ADR-009 préservé** : chaque agent reçoit tous les messages du topic tasks (broadcast), le filtre côté agent (`TaskSpecializationFilter`) est inchangé. Spring Kafka respecte ce modèle via des consumer groups distincts par container.

`transport.kafka.*` reste inchangé (séparé de `platform.kafka-clusters.*` de PDR-020).

**Ne fait PAS** : ne modifie pas `InMemoryExecutionTransport`, `RabbitMQExecutionTransport`, `HttpExecutionTransport`, `SocketExecutionTransport` — uniquement `KafkaExecutionTransport`.

---

## Interfaces Publiques

```java
// ---- Configuration Spring (TransportConfiguration étendue) ----

/**
 * Extension de la configuration transport existante.
 * Ajoute les beans Spring Kafka conditionnels à transport.type=KAFKA.
 * Les beans existants @ConditionalOnProperty restent inchangés.
 */
@Configuration
@ConditionalOnProperty(name = "transport.type", havingValue = "KAFKA")
public class KafkaTransportBeans {

    /**
     * ProducerFactory pour le transport interne.
     * Utilise transport.kafka.bootstrap-servers (pas platform.kafka-clusters.*).
     */
    @Bean
    public ProducerFactory<String, byte[]> transportProducerFactory(
            KafkaTransportProperties props);

    /**
     * KafkaTemplate<String,byte[]> pour publier tasks/events/signals.
     * Remplace le KafkaProducer raw de KafkaExecutionTransport.
     */
    @Bean
    public KafkaTemplate<String, byte[]> transportKafkaTemplate(
            ProducerFactory<String, byte[]> transportProducerFactory);

    /**
     * Factory de containers dynamiques.
     * Un container = un agent (consumer group = agentId, ADR-009).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]>
            transportContainerFactory(KafkaTransportProperties props);
}

// ---- Gestionnaire de containers dynamiques ----

/**
 * Remplace KafkaConsumerManager.
 * Crée et démarre un KafkaMessageListenerContainer par agent enregistré.
 * Thread-safe, containers démarrés/stoppés à la demande.
 *
 * Préserve ADR-009 : groupId = agentId → chaque agent reçoit tous les messages.
 */
public class DynamicKafkaListenerRegistry {

    /**
     * Enregistre un listener pour les tasks destinées à cet agent.
     * Crée un container avec groupId=agentId sur le topic tasks.
     * No-op si un container pour cet agentId existe déjà.
     *
     * @param agentId       identifiant unique de l'agent (= consumer group, ADR-009)
     * @param taskHandler   callback appelé à chaque TaskExecutionRequest reçu
     */
    public void registerTaskListener(String agentId, TaskRequestHandler taskHandler);

    /**
     * Enregistre un listener pour les signals (broadcast, groupId=agentId).
     */
    public void registerSignalListener(String agentId, AgentSignalHandler signalHandler);

    /**
     * Enregistre un listener pour les events (orchestrateur uniquement).
     * groupId = "orchestrator"
     */
    public void registerEventListener(String groupId, ExecutionEventHandler eventHandler);

    /**
     * Stoppe et supprime le container de l'agent donné.
     * Appelé lors du départ d'un agent.
     */
    public void unregisterAgent(String agentId);

    /**
     * Stoppe tous les containers actifs (appelé lors de disconnect()).
     */
    public void stopAll();
}

// ---- KafkaExecutionTransport (interface inchangée) ----
// ExecutionTransport — même interface publique, implémentation interne refactorisée.
// Toutes les méthodes (connect, disconnect, dispatchTask, publishEvent, etc.) gardent
// exactement les mêmes signatures — zéro impact sur les appelants.
```

---

## Règles de Comportement

- **Backward compat totale** : l'interface `ExecutionTransport` ne change pas. Seule l'implémentation `KafkaExecutionTransport` est refactorisée.
- **ADR-009** : `DynamicKafkaListenerRegistry.registerTaskListener(agentId, ...)` utilise `agentId` comme `groupId` Kafka → chaque agent a son propre offset, reçoit tous les messages.
- **connect()** : crée et démarre `KafkaTemplate` via le bean Spring injecté. Plus de `createProducer()` manuel.
- **disconnect()** : appelle `DynamicKafkaListenerRegistry.stopAll()`. Les containers sont stoppés proprement (Spring gère le `close(timeout)`).
- **Idempotence** : `registerTaskListener` pour un agentId déjà enregistré → no-op, log WARN.
- **Codec** : `KafkaMessageCodec` reste identique (encode/decode bytes) — inchangé.
- **`KafkaConsumerManager`** : supprimé et remplacé par `DynamicKafkaListenerRegistry`. Classe marquée `@Deprecated` dans l'Issue, supprimée à la fin.
- **`KafkaSubscription`** : adapté pour wraper le container Spring au lieu d'un thread manuel.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-020 (spring-kafka présent)     → spring-kafka déjà dans le classpath
  platform-transport/pom.xml         → spring-kafka déjà ajouté (PDR-020)
  KafkaTransportProperties           → bootstrap-servers, topics, consumer-group (inchangés)
  KafkaMessageCodec                  → inchangé

Ce PDR est utilisé par :
  PDR-018 (platform-app)             → KafkaExecutionTransport via TransportConfiguration (inchangé)
  PDR-024 (scénarios exemples)       → utilise DISTRIBUTED mode avec KAFKA transport
```

---

## Critères de Done (PDR complet)

- [ ] ISSUE-089, 090, 091 toutes DONE
- [ ] `KafkaConsumerManager` supprimé du codebase
- [ ] `DynamicKafkaListenerRegistry` dans interfaces-registry.md statut STABLE
- [ ] Aucun `Thread.currentThread()`, aucun polling loop manuel dans `platform-transport`
- [ ] Tests d'intégration Testcontainers (Kafka) existants passent (ISSUE-029 tests réutilisables)
- [ ] `mvn test -pl platform-transport -q` → 0 erreur

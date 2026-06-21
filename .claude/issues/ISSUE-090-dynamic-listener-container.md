# ISSUE-090 — DynamicKafkaListenerRegistry remplace KafkaConsumerManager

**PDR** : PDR-021
**Module** : `platform-transport`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-089 (KafkaTransportBeans doit être DONE)
**Estime** : L

---

## Objectif

Remplacer `KafkaConsumerManager` (boucle de polling manuel avec Thread dédiés) par `DynamicKafkaListenerRegistry` qui utilise des `KafkaMessageListenerContainer` Spring créés dynamiquement.

**ADR-009 préservé** : chaque agent a son propre consumer group (`groupId = agentId`), lui permettant de recevoir tous les messages broadcastés sur le topic tasks.

Un container Spring Kafka gère le thread de polling, le rebalance, la reconnexion automatique — éliminant tout le code manuel de `KafkaConsumerManager`.

---

## Fichiers à Créer / Modifier

```
platform-transport/src/main/java/com/performance/platform/transport/kafka/
  ├── DynamicKafkaListenerRegistry.java   — NOUVEAU : remplace KafkaConsumerManager
  ├── KafkaConsumerManager.java           — SUPPRIMÉ (ou marqué @Deprecated si conservé temporairement)
  └── KafkaExecutionTransport.java        — MODIFIÉ : utilise DynamicKafkaListenerRegistry

platform-transport/src/test/java/com/performance/platform/transport/kafka/
  └── DynamicKafkaListenerRegistryTest.java — tests unitaires (mock containers)
```

---

## Interfaces à Implémenter

```java
// DynamicKafkaListenerRegistry.java
/**
 * Registre de KafkaMessageListenerContainer créés dynamiquement.
 * Un container par agent (groupId = agentId, ADR-009).
 * Thread-safe.
 */
public class DynamicKafkaListenerRegistry {

    private static final Logger log = LoggerFactory.getLogger(DynamicKafkaListenerRegistry.class);

    private final ConcurrentKafkaListenerContainerFactory<String, byte[]> containerFactory;
    private final KafkaMessageCodec codec;
    private final String tasksTopic;
    private final String signalsTopic;
    private final String eventsTopic;

    // Map agentId → container(s) actifs
    private final ConcurrentMap<String, List<KafkaMessageListenerContainer<String, byte[]>>>
            containersByAgent = new ConcurrentHashMap<>();

    public DynamicKafkaListenerRegistry(
            ConcurrentKafkaListenerContainerFactory<String, byte[]> containerFactory,
            KafkaMessageCodec codec,
            String tasksTopic, String signalsTopic, String eventsTopic) { ... }

    /**
     * Crée et démarre un container pour les TaskExecutionRequest destinés à cet agent.
     * groupId = agentId (ADR-009 : chaque agent reçoit tous les messages tasks).
     * No-op si un container tasks pour cet agentId existe déjà.
     */
    public void registerTaskListener(String agentId, TaskRequestHandler handler) {
        // 1. Vérifier si container déjà enregistré → log WARN + return
        // 2. Créer ContainerProperties avec tasksTopic + groupId=agentId
        // 3. Créer KafkaMessageListenerContainer<String,byte[]>
        // 4. Setter MessageListener : bytes → codec.decodeTaskRequest() → handler.handle()
        // 5. container.start()
        // 6. Enregistrer dans containersByAgent
    }

    /**
     * Crée et démarre un container pour les AgentSignal (broadcast).
     * groupId = agentId (ADR-009).
     */
    public void registerSignalListener(String agentId, AgentSignalHandler handler) { ... }

    /**
     * Crée et démarre un container pour les ExecutionEvent (orchestrateur).
     * groupId = groupId fourni (typiquement "orchestrator").
     */
    public void registerEventListener(String groupId, ExecutionEventHandler handler) { ... }

    /**
     * Stoppe et supprime tous les containers de l'agent donné.
     */
    public void unregisterAgent(String agentId) {
        List<KafkaMessageListenerContainer<String, byte[]>> containers =
                containersByAgent.remove(agentId);
        if (containers != null) {
            containers.forEach(c -> { c.stop(); log.info("action=container_stopped agentId={}", agentId); });
        }
    }

    /**
     * Stoppe tous les containers actifs (appelé lors de disconnect()).
     */
    public void stopAll() {
        containersByAgent.forEach((agentId, containers) ->
                containers.forEach(KafkaMessageListenerContainer::stop));
        containersByAgent.clear();
        log.info("action=all_containers_stopped");
    }

    // Méthode utilitaire privée
    private KafkaMessageListenerContainer<String, byte[]> createContainer(
            String topic, String groupId, MessageListener<String, byte[]> listener) {
        ContainerProperties props = new ContainerProperties(topic);
        props.setGroupId(groupId);
        props.setMessageListener(listener);
        props.setAckMode(ContainerProperties.AckMode.RECORD);
        KafkaMessageListenerContainer<String, byte[]> container =
                new KafkaMessageListenerContainer<>(
                        containerFactory.getConsumerFactory(), props);
        container.start();
        return container;
    }
}

// KafkaExecutionTransport.java — modifications côté consume
public class KafkaExecutionTransport implements ExecutionTransport {

    // REMPLACÉ : KafkaConsumerManager consumerManager;
    private DynamicKafkaListenerRegistry listenerRegistry;

    @Override
    public void connect() throws TransportException {
        // kafkaTemplate déjà prêt (ISSUE-089)
        listenerRegistry = new DynamicKafkaListenerRegistry(
                containerFactory, codec,
                props.tasksTopic(), props.eventsTopic(), props.signalsTopic());
        connected.set(true);
    }

    @Override
    public void disconnect() {
        if (listenerRegistry != null) listenerRegistry.stopAll();
        kafkaTemplate.flush();
        connected.set(false);
    }

    @Override
    public void receiveTask(TaskRequestHandler handler) {
        // Contexte : appelé avec agentId disponible dans le handler ou un ID session
        // Le registry enregistre un container par invocation
        // L'agentId est extrait du context ou généré si absent
        listenerRegistry.registerTaskListener(resolveAgentId(), handler);
    }

    @Override
    public Subscription subscribe(ExecutionEventHandler handler) {
        listenerRegistry.registerEventListener("orchestrator", handler);
        return new KafkaSubscription(listenerRegistry, "orchestrator");
    }
    // etc.
}
```

---

## Règles Spécifiques

- **ADR-009** : `groupId = agentId` dans `registerTaskListener` et `registerSignalListener`. `registerEventListener` reçoit son groupId en paramètre (typiquement `"orchestrator"`).
- **`ContainerProperties.AckMode.RECORD`** : ack après chaque message traité (comportement identique à l'existant).
- **Container start** : `container.start()` démarre le polling thread Spring — pas de thread manuel.
- **Rebalance** : Spring Kafka gère automatiquement. Aucun code de rebalance à écrire.
- **`KafkaConsumerManager`** : si le refactoring est trop risqué en une seule Issue, le Developer peut laisser la classe en place et ajouter `@Deprecated` + `TODO: remove after ISSUE-090`. La suppression définitive est un critère de Done de ISSUE-091.
- **Tests** : utiliser `EmbeddedKafkaBroker` (spring-kafka-test) pour les tests d'intégration, ou mocker `ConcurrentKafkaListenerContainerFactory` avec Mockito pour les tests unitaires.

---

## Critères de Done

- [ ] `mvn test -pl platform-transport -q` → 0 erreur
- [ ] `KafkaConsumerManager` supprimé ou marqué `@Deprecated` avec TODO
- [ ] Aucun `Thread.new(...)` manuel dans platform-transport (hors Virtual Threads)
- [ ] Aucun `while(running)` polling loop manuel dans platform-transport
- [ ] Test unitaire : `registerTaskListener(agentId, handler)` → container créé, `start()` appelé
- [ ] Test unitaire : `unregisterAgent(agentId)` → container stoppé
- [ ] Test unitaire : `stopAll()` → tous les containers stoppés
- [ ] Test unitaire : 2 agents → 2 containers distincts (ADR-009)
- [ ] `.claude/progress.md` mis à jour : ISSUE-090 → DONE

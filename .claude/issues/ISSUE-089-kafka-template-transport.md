# ISSUE-089 — KafkaTemplate replace raw KafkaProducer dans KafkaExecutionTransport

**PDR** : PDR-021
**Module** : `platform-transport`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-086 (spring-kafka dans le classpath après PDR-020)
**Estime** : M

---

## Objectif

Remplacer le `KafkaProducer<String, byte[]>` raw créé manuellement dans `KafkaExecutionTransport` par un `KafkaTemplate<String, byte[]>` Spring injecté.

Périmètre : uniquement le côté **produce** de `KafkaExecutionTransport` :
- `dispatchTask(TaskExecutionRequest)` → `template.send(tasksTopic, key, bytes)`
- `publishEvent(ExecutionEvent)` → `template.send(eventsTopic, key, bytes)`
- `publishAgentEvent(AgentLifecycleEvent)` → `template.send(eventsTopic, key, bytes)`
- `broadcastSignal(AgentSignal)` → `template.send(signalsTopic, key, bytes)`

La méthode `createProducer()` et le champ `KafkaProducer producer` sont supprimés. `KafkaTemplate` est injecté via constructeur (fourni par `KafkaTransportBeans` — ISSUE-091).

Le côté consume (`KafkaConsumerManager`) sera traité dans ISSUE-090.

---

## Fichiers à Modifier

```
platform-transport/src/main/java/com/performance/platform/transport/kafka/
  └── KafkaExecutionTransport.java         — champ producer → template, send via template

platform-transport/src/main/java/com/performance/platform/transport/config/
  └── KafkaTransportBeans.java             — NOUVEAU : @Bean KafkaTemplate<String,byte[]>
                                             (peut être dans TransportConfiguration existant)

platform-transport/src/test/java/com/performance/platform/transport/kafka/
  └── KafkaExecutionTransportTest.java     — adapter mocks pour KafkaTemplate
```

---

## Interfaces à Implémenter

```java
// KafkaExecutionTransport.java — champs modifiés
public class KafkaExecutionTransport implements ExecutionTransport {

    // SUPPRIMÉ : private KafkaProducer<String, byte[]> producer;
    // AJOUTÉ :
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    // ... autres champs inchangés (props, codec, connected, handlers)

    // Constructeur MODIFIÉ (KafkaTemplate injecté)
    public KafkaExecutionTransport(KafkaTransportProperties props,
                                   KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.props = props;
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate);
        this.codec = new KafkaMessageCodec();
    }

    @Override
    public void connect() throws TransportException {
        // SUPPRIMÉ : this.producer = createProducer();
        // kafkaTemplate est déjà prêt (Spring le gère)
        consumerManager = ...;  // inchangé (ISSUE-090 s'en chargera)
        connected.set(true);
    }

    @Override
    public void disconnect() {
        // SUPPRIMÉ : closeQuietly(producer);
        // kafkaTemplate.flush() + template géré par Spring
        if (consumerManager != null) consumerManager.stop();
        connected.set(false);
    }

    // sendRecord — MODIFIÉ
    private void sendRecord(String topic, String key, byte[] data,
                            String action, String executionId) {
        try {
            kafkaTemplate.send(topic, key, data).get();  // Virtual Thread friendly
            log.debug("action={} executionId={} topic={} key={}",
                    action, executionId, topic, key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Interrupted while sending to " + topic, e);
        } catch (ExecutionException e) {
            throw new TransportException("Failed to send to " + topic + ": "
                    + e.getCause().getMessage(), e.getCause());
        }
    }

    // SUPPRIMÉ : createProducer(), closeQuietly()
}

// KafkaTransportBeans.java (nouveau fichier dans config/)
@Configuration
@ConditionalOnProperty(name = "transport.type", havingValue = "KAFKA")
public class KafkaTransportBeans {

    @Bean("transportProducerFactory")
    public ProducerFactory<String, byte[]> transportProducerFactory(
            KafkaTransportProperties props) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,        props.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,     StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,   ByteArraySerializer.class,
                ProducerConfig.ACKS_CONFIG,                     props.producerAcks()
        );
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean("transportKafkaTemplate")
    public KafkaTemplate<String, byte[]> transportKafkaTemplate(
            @Qualifier("transportProducerFactory")
            ProducerFactory<String, byte[]> transportProducerFactory) {
        return new KafkaTemplate<>(transportProducerFactory);
    }
}
```

---

## Règles Spécifiques

- **Qualifiers** : nommer les beans `transportProducerFactory` et `transportKafkaTemplate` pour éviter collision avec les beans PDR-020 (`platform.kafka-clusters.*`).
- **`send().get()`** : appel synchrone — dans un Virtual Thread c'est acceptable. Même comportement que le `producer.send(record).get()` existant.
- **flush() sur disconnect** : `kafkaTemplate.flush()` avant de setter `connected=false`.
- **Tests** : mocker `KafkaTemplate` avec `@MockBean` ou `Mockito.mock()`. Vérifier que `send(topic, key, data)` est appelé avec les bons arguments sur `dispatchTask`, `publishEvent`, `broadcastSignal`.

---

## Critères de Done

- [ ] `mvn test -pl platform-transport -q` → 0 erreur
- [ ] Aucun `KafkaProducer` raw dans `KafkaExecutionTransport`
- [ ] Aucun `new Properties()` + `new KafkaProducer(...)` dans le module platform-transport
- [ ] Test : `dispatchTask(request)` → `kafkaTemplate.send(tasksTopic, ...)` appelé
- [ ] Test : `publishEvent(event)` → `kafkaTemplate.send(eventsTopic, ...)` appelé
- [ ] Test : `broadcastSignal(signal)` → `kafkaTemplate.send(signalsTopic, ...)` appelé
- [ ] Test : `connect()` ne crée pas de producer raw
- [ ] `.claude/progress.md` mis à jour : ISSUE-089 → DONE

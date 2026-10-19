# PDR-052 — Transport Interface Segregation

**Module Maven** : `platform-transport` (+ consommateurs `platform-execution-engine`, `platform-agent-runtime`, `platform-app`)
**Package** : `com.performance.platform.transport`
**Statut** : WAITING
**Specs de référence** : `improvements.md` A4 ; `ADR-002-transport-abstraction.md` ; `ADR-012-agent-lifecycle-event.md`
**Dépend de** : PDR-007/008 (transport core + implémentations)
**Issues** : ISSUE-191, ISSUE-192, ISSUE-193

---

## Responsabilité

`ExecutionTransport` est une interface grasse : 12 méthodes × 5 implémentations.
Ajouter une méthode coûte 5 classes ; le javadoc avertit « toute modification
nécessite un ADR » — symptôme, pas garde-fou. Segmentation (Interface Segregation).

> ADR requise (ADR-028) : remplacement de l'interface publique ⚡ `ExecutionTransport`
> par des ports étroits.

---

## Interfaces Publiques

```java
package com.performance.platform.transport;

/** Dispatch de tâches (orchestrateur → agents). */
public interface TaskChannel {
    void dispatchTask(TaskExecutionRequest request);
    void receiveTask(TaskRequestHandler handler);
}

/** Signaux broadcast (orchestrateur → agents). */
public interface SignalChannel {
    void broadcastSignal(AgentSignal signal);
    void receiveSignal(AgentSignalHandler handler);
}

/** Événements d'exécution (agent → orchestrateur). */
public interface EventChannel {
    void publishEvent(ExecutionEvent event);
    Subscription subscribe(ExecutionEventHandler handler);
}

/** Événements de cycle de vie d'agent (agent → orchestrateur). */
public interface LifecycleChannel {
    void publishAgentEvent(AgentLifecycleEvent event);
    Subscription subscribeAgentEvents(AgentLifecycleEventHandler handler);
}

/** Cycle de vie de connexion. */
public interface ConnectionLifecycle {
    void connect() throws TransportException;
    void disconnect();
    boolean isConnected();
}
```

`ExecutionTransport` est soit supprimé, soit conservé comme **façade composite**
(`extends TaskChannel, SignalChannel, EventChannel, LifecycleChannel, ConnectionLifecycle`)
le temps de la migration (ISSUE-191) — puis retiré une fois les consommateurs migrés.
`getType()` disparaît (plus de `if/switch` sur le type, CF-03).

---

## Règles de Comportement

- Chaque implémentation (InMemory, Kafka, RabbitMQ, HTTP, Socket) n'implémente
  que les canaux qu'elle supporte réellement. Une capacité non supportée → `TransportException`
  claire, pas un no-op silencieux.
- Les consommateurs (RemoteStepDispatcher, TaskAgentRuntime, ingestion lifecycle)
  ne dépendent que du/des canaux dont ils ont besoin.
- La segmentation est un refactor mécanique : aucun changement de comportement.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-007/008 (ExecutionTransport, InMemory/Kafka/RabbitMQ/HTTP/Socket)

Ce PDR est utilisé par :
  PDR-050 (TaskAgentRuntime + ingestion), PDR-045 (ingestion lifecycle)
```

---

## Critères de Done (PDR complet)

- [ ] Ports étroits définis + façade de migration (ISSUE-191)
- [ ] 5 implémentations migrées (ISSUE-192)
- [ ] Consommateurs migrés sur les ports étroits (ISSUE-193)
- [ ] `mvn test -pl platform-transport,platform-execution-engine,platform-agent-runtime -q` → 0 erreur

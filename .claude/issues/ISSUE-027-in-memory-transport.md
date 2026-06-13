# ISSUE-027 — InMemoryExecutionTransport (mode LOCAL + tests)

**PDR** : PDR-007
**Module** : `platform-transport`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-025, ISSUE-026
**Estime** : M

---

## Objectif

Implémenter `InMemoryExecutionTransport` (queues mémoire, livraison synchrone par défaut)
sélectionné via `@ConditionalOnProperty(transport.type=IN_MEMORY)`.

## Fichiers à Créer

```
platform-transport/src/main/java/com/performance/platform/transport/inmemory/
  └── InMemoryExecutionTransport.java

platform-transport/src/test/java/com/performance/platform/transport/inmemory/
  └── InMemoryExecutionTransportTest.java — dispatch→receiveTask, publishEvent→subscribe, broadcastSignal→receiveSignal
```

## Interfaces à Implémenter

```java
@Component
@ConditionalOnProperty(name = "transport.type", havingValue = "IN_MEMORY")
public class InMemoryExecutionTransport implements ExecutionTransport {
    public TransportType getType() { return TransportType.IN_MEMORY; }
    // queues internes ; asyncDelivery=false par défaut (synchrone, déterministe)
}
```

## Règles Spécifiques

- `dispatchTask` invoque immédiatement les `TaskRequestHandler` enregistrés (synchrone).
- `publishEvent` notifie les `ExecutionEventHandler` abonnés.
- `subscribe` retourne une `Subscription` annulable.
- `getType()` retourne `IN_MEMORY`.

## Critères de Done

- [ ] `mvn test -pl platform-transport -q` → 0 erreur
- [ ] Round-trip dispatch→receive, publish→subscribe, signal→receiveSignal
- [ ] `Subscription.cancel()` stoppe la réception
- [ ] `.claude/progress.md` mis à jour : ISSUE-027 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `InMemoryExecutionTransport` → STABLE

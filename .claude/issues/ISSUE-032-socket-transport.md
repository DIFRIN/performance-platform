# ISSUE-032 — SocketExecutionTransport (broadcast connexions actives)

**PDR** : PDR-008
**Module** : `platform-transport`
**Statut** : IN REVIEW
**Priorité** : P2
**Bloquée par** : ISSUE-027, ISSUE-028
**Estime** : L

---

## Objectif

Implémenter `SocketExecutionTransport` : broadcast sur toutes les connexions socket actives,
reconnexion best-effort côté agent.

## Fichiers à Créer

```
platform-transport/src/main/java/com/performance/platform/transport/socket/
  ├── SocketExecutionTransport.java
  └── SocketConnectionRegistry.java   — connexions actives côté orchestrateur

platform-transport/src/test/java/com/performance/platform/transport/socket/
  └── SocketExecutionTransportTest.java — loopback round-trip
```

## Interfaces à Implémenter

```java
@Component
@ConditionalOnProperty(name = "transport.type", havingValue = "SOCKET")
public class SocketExecutionTransport implements ExecutionTransport {
    public SocketExecutionTransport(SocketTransportProperties props) { /* ... */ }
    public TransportType getType() { return TransportType.SOCKET; }
}
```

## Règles Spécifiques

- Orchestrateur : `dispatchTask`/`broadcastSignal` → envoi sur toutes les connexions actives.
- Agent : reconnexion toutes les `reconnectIntervalMs` en cas de perte.
- Best-effort (pas de garantie at-least-once) — idempotence côté agent via MessageId.
- I/O bloquant socket sous Virtual Threads.

## Critères de Done

- [ ] `mvn test -pl platform-transport -q` → 0 erreur
- [ ] Round-trip loopback (client+serveur en mémoire)
- [ ] `getType()` == SOCKET
- [ ] `.claude/progress.md` mis à jour : ISSUE-032 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `SocketExecutionTransport` → STABLE

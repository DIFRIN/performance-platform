# ISSUE-191 — Define Segregated Transport Ports

**PDR** : PDR-052
**Module** : `platform-transport`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : —
**Estime** : M (1-3h)

---

## Objectif

Définir les ports étroits remplaçant `ExecutionTransport` grasse (A4) : `TaskChannel`,
`SignalChannel`, `EventChannel`, `LifecycleChannel`, `ConnectionLifecycle`, et une
façade composite de migration.

> ADR requise (ADR-028).

## Fichiers à Créer

```
platform-transport/src/main/java/com/performance/platform/transport/
  ├── TaskChannel.java            — dispatchTask + receiveTask
  ├── SignalChannel.java          — broadcastSignal + receiveSignal
  ├── EventChannel.java           — publishEvent + subscribe
  ├── LifecycleChannel.java       — publishAgentEvent + subscribeAgentEvents
  └── ConnectionLifecycle.java    — connect/disconnect/isConnected
```

## Interfaces à Implémenter

```java
public interface TaskChannel { void dispatchTask(TaskExecutionRequest r); void receiveTask(TaskRequestHandler h); }
public interface SignalChannel { void broadcastSignal(AgentSignal s); void receiveSignal(AgentSignalHandler h); }
public interface EventChannel { void publishEvent(ExecutionEvent e); Subscription subscribe(ExecutionEventHandler h); }
public interface LifecycleChannel { void publishAgentEvent(AgentLifecycleEvent e); Subscription subscribeAgentEvents(AgentLifecycleEventHandler h); }
public interface ConnectionLifecycle { void connect() throws TransportException; void disconnect(); boolean isConnected(); }

// façade de migration (temporaire)
public interface ExecutionTransport extends TaskChannel, SignalChannel, EventChannel, LifecycleChannel, ConnectionLifecycle {}
```

## Règles Spécifiques

- `getType()` supprimé (plus de `if/switch` sur le type, CF-03).
- La façade `ExecutionTransport` est conservée le temps de la migration, puis retirée (ISSUE-193).

## Critères de Done

- [ ] Ports étroits + façade créés
- [ ] `mvn test -pl platform-transport -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-191 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (ports étroits)

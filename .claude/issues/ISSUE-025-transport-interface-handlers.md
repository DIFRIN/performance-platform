# ISSUE-025 — Interface ExecutionTransport + handlers + Subscription

**PDR** : PDR-007
**Module** : `platform-transport`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-009
**Estime** : M

---

## Objectif

Créer le module `platform-transport` et l'interface publique critique `ExecutionTransport`
(⚡) avec ses handlers fonctionnels, `Subscription` et `TransportException`.

## Fichiers à Créer

```
platform-transport/pom.xml — dépend de platform-domain
platform-transport/src/main/java/com/performance/platform/transport/
  ├── ExecutionTransport.java
  ├── TaskRequestHandler.java
  ├── AgentSignalHandler.java
  ├── ExecutionEventHandler.java
  ├── Subscription.java
  └── TransportException.java

platform-transport/src/test/java/com/performance/platform/transport/
  └── TransportInterfaceTest.java — vérifie signatures compilent (impl no-op)
```

## Interfaces à Implémenter

```java
public interface ExecutionTransport {
    void dispatchTask(TaskExecutionRequest request);
    void broadcastSignal(AgentSignal signal);
    void publishEvent(ExecutionEvent event);
    Subscription subscribe(ExecutionEventHandler handler);
    void receiveTask(TaskRequestHandler handler);
    void receiveSignal(AgentSignalHandler handler);
    void connect() throws TransportException;
    void disconnect();
    boolean isConnected();
    TransportType getType();
}
@FunctionalInterface public interface TaskRequestHandler { void onRequest(TaskExecutionRequest request); }
@FunctionalInterface public interface AgentSignalHandler { void onSignal(AgentSignal signal); }
@FunctionalInterface public interface ExecutionEventHandler { void onEvent(ExecutionEvent event); }
public interface Subscription { void cancel(); boolean isActive(); }
public class TransportException extends RuntimeException {
    public TransportException(String message, Throwable cause) { super(message, cause); }
}
```

## Règles Spécifiques

- `ExecutionTransport` est interface publique critique ⚡ — toute modif = ADR.
- `dispatchTask` est un broadcast — pas de targetAgentId.

## Critères de Done

- [ ] `mvn test -pl platform-transport -q` → 0 erreur
- [ ] Une impl no-op compile
- [ ] `.claude/progress.md` mis à jour : ISSUE-025 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `ExecutionTransport` ⚡ → STABLE

# ISSUE-034 — AgentRegistrationPort + heartbeat (côté agent)

**PDR** : PDR-009
**Module** : `platform-agent-runtime`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-033
**Estime** : M

---

## Objectif

Implémenter l'enregistrement et le heartbeat de l'agent via le transport
(`AgentRegistrationPort`).

## Fichiers à Créer

```
platform-agent-runtime/src/main/java/com/performance/platform/agent/registration/
  ├── AgentRegistrationPort.java
  ├── TransportAgentRegistration.java   — publie via ExecutionTransport
  ├── HeartbeatScheduler.java           — heartbeat périodique
  └── RegistrationException.java

platform-agent-runtime/src/test/java/com/performance/platform/agent/registration/
  └── TransportAgentRegistrationTest.java
```

## Interfaces à Implémenter

```java
public interface AgentRegistrationPort {
    void register(AgentDescriptor descriptor) throws RegistrationException;
    void deregister(AgentId agentId);
    void sendHeartbeat(AgentId agentId, AgentHeartbeat heartbeat);
}
public class RegistrationException extends RuntimeException {
    public RegistrationException(String message, Throwable cause) { super(message, cause); }
}
```

## Règles Spécifiques

- `register` publie `ExecutionEvent(AGENT_REGISTERED, payload=AgentDescriptor)`.
- `sendHeartbeat` publie `ExecutionEvent(AGENT_HEARTBEAT, payload=AgentHeartbeat)`.
- Heartbeat toutes les `heartbeat.intervalSeconds` ; `ttl >= 3 × interval`.
- Scheduler sous Virtual Threads.

## Critères de Done

- [ ] `mvn test -pl platform-agent-runtime -q` → 0 erreur
- [ ] `register` publie l'event AGENT_REGISTERED (mock transport)
- [ ] Heartbeat publié à intervalle régulier
- [ ] `progress.md` mis à jour : ISSUE-034 → DONE
- [ ] `context/interfaces-registry.md` : `AgentRegistrationPort` → STABLE

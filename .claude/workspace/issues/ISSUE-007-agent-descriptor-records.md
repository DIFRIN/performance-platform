# ISSUE-007 — Records Agent (Descriptor / Capabilities / Heartbeat) + ArchUnit

**PDR** : PDR-001
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-001, ISSUE-002
**Estime** : M

---

## Objectif

Créer les records agent et un test ArchUnit garantissant 0 dépendance framework dans
`platform-domain`.

## Fichiers à Créer

```
platform-domain/src/main/java/com/performance/platform/domain/agent/
  ├── AgentDescriptor.java
  ├── AgentCapabilities.java
  └── AgentHeartbeat.java

platform-domain/src/test/java/com/performance/platform/domain/
  ├── agent/AgentDescriptorTest.java — canExecute(), copie défensive supportedTaskNames
  └── arch/DomainArchitectureTest.java — ArchUnit : 0 import Spring/JPA/Jackson
```

## Interfaces à Implémenter

```java
public record AgentDescriptor(AgentId id, String name, String host, int port,
    String httpCallbackUrl, Set<String> supportedTaskNames, AgentCapabilities capabilities,
    AgentState state, Instant registeredAt, Instant lastHeartbeatAt, Duration registrationTtl) {
    public AgentDescriptor {
        supportedTaskNames = supportedTaskNames == null ? Set.of() : Set.copyOf(supportedTaskNames);
    }
    public boolean canExecute(String taskName) { return supportedTaskNames.contains(taskName); }
}

public record AgentCapabilities(int maxConcurrentTasks, String version) {}

public record AgentHeartbeat(AgentId agentId, AgentState state, int activeTasks, Instant sentAt) {}
```

## Règles Spécifiques

- `httpCallbackUrl` nullable (transport non-HTTP).
- `supportedTaskNames` immuable, déclaré statiquement.
- Test ArchUnit : interdire `org.springframework..`, `jakarta.persistence..`, `com.fasterxml.jackson..` dans `com.performance.platform.domain..`.

## Critères de Done

- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] `AgentDescriptor.canExecute("gatling")` reflète `supportedTaskNames`
- [ ] Test ArchUnit passe (aucun import framework détecté)
- [ ] `.claude/progress.md` mis à jour : ISSUE-007 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `AgentDescriptor`, `AgentCapabilities`, `AgentHeartbeat` → STABLE

# ISSUE-173 — Lifecycle Ingestion Bridge + AgentTtlMonitor Wiring

**PDR** : PDR-045
**Module** : `platform-agent-runtime`
**Statut** : DONE
**Priorité** : P0
**Bloquée par** : ISSUE-172
**Estime** : L (3-6h)

---

## Objectif

Câbler opérationnellement la détection de perte d'agent (B4). Créer le pont
transport→registre (`AgentLifecycleIngestion`), démarrer/arrêter `AgentTtlMonitor`,
et publier `AgentLost` (domaine) à chaque expiration TTL.

## Fichiers à Créer / Modifier

```
execution/platform-agent-runtime/src/main/java/com/performance/platform/agent/registry/
  ├── AgentLifecycleIngestion.java        — NOUVEAU (SmartLifecycle + AgentLifecycleEventHandler)
  └── config/AgentRegistryConfiguration.java — ajouter @Bean AgentTtlMonitor

execution/platform-agent-runtime/src/test/java/com/performance/platform/agent/registry/
  ├── AgentLifecycleIngestionTest.java    — NOUVEAU
  └── AgentTtlMonitorWiringTest.java      — NOUVEAU (start/stop + AgentLost publié)
```

## Interfaces à Implémenter

```java
@Component
@ConditionalOnExpression("'${runtime.mode:LOCAL}'.equals('DISTRIBUTED') && '${runtime.role:NONE}'.equals('ORCHESTRATOR')")
public class AgentLifecycleIngestion implements AgentLifecycleEventHandler, SmartLifecycle {
    public AgentLifecycleIngestion(ExecutionTransport transport, AgentRegistry registry,
                                   ApplicationEventPublisher events, AgentTtlMonitor monitor);
    @Override public void onEvent(AgentLifecycleEvent event);
    @Override public void start();  // transport.subscribeAgentEvents(this); monitor.start();
    @Override public void stop();   // subscription.cancel(); monitor.stop();
}
```

## Règles Spécifiques

- `onEvent` : REGISTERED→`onAgentRegistered(decodeDescriptor)`, HEARTBEAT→`onAgentHeartbeat`,
  DEREGISTERED→`onAgentDeregistered` ; payload illisible → WARN + ignore.
- Le `AgentTtlMonitor` bean reçoit un `onExpired` qui publie `new AgentLost(agentId, "heartbeat TTL expired", now)`.
- Le registre `InMemoryAgentRegistry` est ainsi enfin alimenté par le transport.

## Critères de Done

- [ ] `AgentLifecycleIngestion` alimente le registre depuis le transport
- [ ] `AgentTtlMonitor` démarré/arrêté par le cycle de vie Spring
- [ ] `AgentLost` publié à l'expiration TTL
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-173 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (AgentLifecycleIngestion, AgentTtlMonitor bean)

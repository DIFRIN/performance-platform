# PDR-045 — Agent Lifecycle Ingestion & Loss Fast-Fail

**Module Maven** : `platform-agent-runtime`, `platform-transport`, `platform-execution-engine`
**Package** : `com.performance.platform.agent.registry`, `com.performance.platform.transport`, `com.performance.platform.engine`
**Statut** : WAITING
**Specs de référence** : `improvements.md` B3, B4, A6 ; `ADR-012-agent-lifecycle-event.md` ; `ADR-011-multi-claim-all-complete.md`
**Dépend de** : PDR-044 (onAgentLost), PDR-007/009 (transport + registry)
**Issues** : ISSUE-172, ISSUE-173, ISSUE-174

---

## Responsabilité

Rendre opérationnelle la détection de perte d'agent, qui est présente
architecturalement mais absente à l'exécution (B4). Aujourd'hui :
- `AgentTtlMonitor` n'est ni bean, ni démarré, ni câblé ;
- personne n'appelle `ExecutionTransport.subscribeAgentEvents(...)` ni
  `AgentRegistryPort.onAgentRegistered/onAgentHeartbeat/onAgentDeregistered` ;
- le `InMemoryAgentRegistry` n'est jamais alimenté par les événements du transport ;
- `AgentLifecycleEvent` ne connaît que REGISTERED/HEARTBEAT/DEREGISTERED (pas de LOST) ;
- le payload `Map<String,Object>` est construit à la main (dérive possible, A6/T8).

Ce PDR construit le pont transport→registre, démarre le moniteur TTL, publie
`AgentLost` (event de domaine) + `AgentLifecycleEvent.AGENT_LOST`, et fait
échouer rapidement un step dont un agent claimer est perdu.

> ADR requise (ADR-033) : modèle de cycle de vie d'agent — ingestion par
> souscription, type LOST, typage du payload, sort des records morts A6.

---

## Interfaces Publiques

```java
// platform-transport
package com.performance.platform.transport;

public record AgentLifecycleEvent(EventId id, AgentId agentId, String eventType,
                                  Map<String, Object> payload, Instant occurredAt) {
    public static final String AGENT_REGISTERED   = "AgentRegistered";
    public static final String AGENT_HEARTBEAT    = "AgentHeartbeat";
    public static final String AGENT_DEREGISTERED = "AgentDeregistered";
    public static final String AGENT_LOST         = "AgentLost";     // NOUVEAU
    // payload copy-on-write inchangé
}

/** Codec typé remplaçant les Maps construites à la main (TransportAgentRegistration). */
public final class AgentLifecyclePayloadCodec {
    public static Map<String, Object> encodeDescriptor(AgentDescriptor d);
    public static AgentDescriptor      decodeDescriptor(Map<String, Object> payload);
    public static Map<String, Object> encodeHeartbeat(AgentHeartbeat h);
    public static AgentHeartbeat       decodeHeartbeat(AgentId agentId, Map<String, Object> payload);
}
```

```java
// platform-agent-runtime
package com.performance.platform.agent.registry;

/**
 * Pont transport → registre côté orchestrateur. Souscrit aux événements
 * de cycle de vie du transport et alimente {@link AgentRegistryPort}.
 */
@Component
@ConditionalOnExpression(
    "'${runtime.mode:LOCAL}'.equals('DISTRIBUTED') && '${runtime.role:NONE}'.equals('ORCHESTRATOR')")
public class AgentLifecycleIngestion
        implements AgentLifecycleEventHandler, SmartLifecycle {

    public AgentLifecycleIngestion(
            ExecutionTransport transport,
            AgentRegistry registry,
            ApplicationEventPublisher events,
            AgentTtlMonitor monitor);

    @Override public void onEvent(AgentLifecycleEvent event);
    // AGENT_REGISTERED   → registry.onAgentRegistered(decodeDescriptor(payload))
    // AGENT_HEARTBEAT    → registry.onAgentHeartbeat(agentId, decodeHeartbeat(agentId, payload))
    // AGENT_DEREGISTERED → registry.onAgentDeregistered(agentId)
    // AGENT_LOST         → (défensif, no-op — la source de vérité est le TTL monitor)

    @Override public void start();  // transport.subscribeAgentEvents(this) ; monitor.start()
    @Override public void stop();   // subscription.cancel() ; monitor.stop()
    @Override public boolean isRunning();
}
```

```java
// platform-agent-runtime — extension de AgentRegistryConfiguration
@Bean
@ConditionalOnExpression(
    "'${runtime.mode:LOCAL}'.equals('DISTRIBUTED') && '${runtime.role:NONE}'.equals('ORCHESTRATOR')")
public AgentTtlMonitor agentTtlMonitor(AgentRegistry registry, ApplicationEventPublisher events) {
    return new AgentTtlMonitor(
        (TtlTrackable) registry,
        /*checkIntervalSeconds*/ 30,
        agentId -> {
            events.publishEvent(new AgentLost(agentId, "heartbeat TTL expired", Instant.now()));
            // + publication transport AGENT_LOST si un canal distant doit propager la perte
        });
}
```

---

## Règles de Comportement

- **Source de vérité de la perte = TTL monitor** (expiration `lastHeartbeatAt + registrationTtl`),
  pas un event LOST reçu du transport (un agent mort n'émet pas d'event).
- `AgentLost` (domaine) est publié via `ApplicationEventPublisher` à chaque éviction TTL.
- `AgentLifecycleEvent.AGENT_LOST` est disponible pour propager la perte sur le canal
  lifecycle si un orchestrateur redondant en a besoin (optionnel, hors scope immédiat).
- L'ingestion est idempotente et tolérante : un payload illisible est loggé en WARN et ignoré.
- Le fast-fail (ISSUE-174) écoute `AgentLost` et appelle `tracker.onAgentLost(agentId)`.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-044 (TaskCorrelationTracker.onAgentLost)
  PDR-009 (AgentRegistry/InMemoryAgentRegistry/AgentTtlMonitor/TtlTrackable)
  PDR-007 (ExecutionTransport.subscribeAgentEvents, AgentLifecycleEvent)

Ce PDR est utilisé par :
  PDR-050 (consolidation) → le runtime unifié garde cette ingestion
```

---

## Critères de Done (PDR complet)

- [ ] `AgentLifecyclePayloadCodec` round-trip `AgentDescriptor`/`AgentHeartbeat` (T8, ISSUE-172)
- [ ] `AgentLifecycleIngestion` souscrit et alimente le registre (ISSUE-173)
- [ ] `AgentTtlMonitor` démarré/arrêté par le cycle de vie Spring (ISSUE-173)
- [ ] `AgentLost` publié à l'expiration TTL (ISSUE-173)
- [ ] Un agent tué en cours de tâche → step échoue << timeout + `AgentLost` émis (T4, ISSUE-174)

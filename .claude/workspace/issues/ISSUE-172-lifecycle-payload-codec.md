# ISSUE-172 — Typed Lifecycle Payload Codec + AGENT_LOST

**PDR** : PDR-045
**Module** : `platform-transport`
**Statut** : DONE
**Priorité** : P0
**Bloquée par** : —
**Estime** : M (1-3h)

---

## Objectif

Remplacer les `Map<String,Object>` construites à la main dans `TransportAgentRegistration`
par un codec typé (round-trip `AgentDescriptor`/`AgentHeartbeat`), ajouter le type
`AGENT_LOST` à `AgentLifecycleEvent`, et le test round-trip T8.

## Fichiers à Créer / Modifier

```
platform-transport/src/main/java/com/performance/platform/transport/
  ├── AgentLifecycleEvent.java            — ajouter AGENT_LOST
  └── AgentLifecyclePayloadCodec.java     — NOUVEAU

platform-transport/src/test/java/com/performance/platform/transport/
  └── AgentLifecycleRoundTripTest.java    — NOUVEAU (T8)
```

## Interfaces à Implémenter

```java
public static final String AGENT_LOST = "AgentLost";   // AgentLifecycleEvent

public final class AgentLifecyclePayloadCodec {
    public static Map<String,Object> encodeDescriptor(AgentDescriptor d);
    public static AgentDescriptor      decodeDescriptor(Map<String,Object> payload);
    public static Map<String,Object> encodeHeartbeat(AgentHeartbeat h);
    public static AgentHeartbeat       decodeHeartbeat(AgentId agentId, Map<String,Object> payload);
}
```

## Règles Spécifiques

- `decodeDescriptor` reconstruit `AgentCapabilities` depuis `capabilities` du payload.
- `decodeHeartbeat` retourne `AgentHeartbeat` avec `sentAt` parsé depuis `Instant`.
- Le codec est la SEULE source de sérialisation lifecycle (plus de `toPayload` à la main).

## Critères de Done

- [ ] `AgentLifecyclePayloadCodec` round-trip `AgentDescriptor` + `AgentHeartbeat` (T8)
- [ ] `TransportAgentRegistration` migré sur le codec (suppression des `toPayload` manuels)
- [ ] `AGENT_LOST` ajouté
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-172 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (codec + AGENT_LOST)

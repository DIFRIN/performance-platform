# ADR-033 — Ingestion du cycle de vie agent, `AGENT_LOST` et fast-fail sur perte de claim

**Date** : 2026-10-19
**Statut** : ACCEPTED
**Décideurs** : Architect + implémentation (Claude Code), sur constat de l'audit `improvements.md` (A6/B3/B4)
**Raffine** : ADR-012 (canal lifecycle séparé), ADR-011 (multi-claim `ALL_COMPLETE`)
**Impacte interfaces publiques** : OUI — ajout de `AGENT_LOST`, `TaskCorrelationTracker.onAgentLost/release`, `LifecycleSignalHandler`

---

## Contexte

L'audit a mis en évidence une chaîne opérationnellement absente :

- rien en production n'appelait `ExecutionTransport.subscribeAgentEvents` ni
  `AgentRegistryPort.onAgentRegistered` : le registre de l'orchestrateur restait vide ;
- `AgentTtlMonitor` n'était ni bean, ni démarré ;
- en `ALL_COMPLETE`, un agent mort ne complétait ni n'échouait jamais → stall
  jusqu'au timeout (300 s par défaut) ;
- `AgentLost` existait dans le domaine mais n'était jamais émis.

## Décision

1. **Codec typé** : `AgentLifecyclePayloadCodec` encode/décode
   `AgentDescriptor` et `AgentHeartbeat` ; `TransportAgentRegistration` l'utilise
   (fin du payload `Map` maintenu manuellement).
2. **Ingestion** : `AgentLifecycleIngestion` s'abonne à
   `transport.subscribeAgentEvents` et alimente `AgentRegistryPort`
   (`registered`, `heartbeat`, `deregistered`, `lost`).
3. **TTL** : `AgentTtlMonitor` devient un bean Spring (init `start`, destroy
   `stop`) créé par `AgentRegistryConfiguration` en DISTRIBUTED + ORCHESTRATOR ;
   son callback publie `AgentLost` (Spring event).
4. **Perte de claim** : `TaskCorrelationTracker.onAgentLost(agentId)` marque
   comme échoués les claims non terminés ; `FIRST_COMPLETE` fast-fail si tous les
   claimers ont échoué. `RemoteExecutionEngine` écoute `AgentLost`.
5. **Éviction** : `TaskCorrelationTracker.release(messageId)` supprime l'état de
   corrélation après consommation ; `RemoteStepDispatcher` l'appelle en `finally`.
6. **Nouvelle constante** `AgentLifecycleEvent.AGENT_LOST`.

## Conséquences

- Le registre orchestrateur est réellement alimenté en DISTRIBUTED.
- Un agent perdu libère les steps en cours sans attendre le timeout.
- Les événements tardifs sont ignorés : `onTransportEvent` sort si
  `tracker.taskIdFor(...)` est nul après `release`.
- Un IT de perte d'agent (`AgentLossRecoveryIT`) reste à écrire (ISSUE-174
  IN_PROGRESS) ; la logique est couverte par des tests unitaires.

## Preuves

- `AgentLifecycleIngestionTest` (round-trip, registration, heartbeat, deregister, lost, unsubscribe).
- `DefaultTaskCorrelationTrackerTest` (onAgentLost, fast-fail, release, 10 000 cycles sans fuite).
- `AgentRegistryConfigurationTest` (beans ingestion + monitor).

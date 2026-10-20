# ADR-028 — Segregation de `ExecutionTransport` en ports etroits

**Date** : 2026-10-20
**Statut** : ACCEPTED
**Décideurs** : Architect + implémentation (Claude Code), sur constat de l'audit `improvements.md` (A4)
**Raffine** : ADR-002 (transport), ADR-006 (transport.type), ADR-012 (lifecycle)
**Impacte interfaces publiques** : OUI — `ExecutionTransport` devient une facade composite, `getType()` est supprime

---

## Contexte

`ExecutionTransport` exposait 12 methodes pour 5 implementations. Ajouter une
methode imposait de modifier les 5 classes, et `getType()` encourageait les
`if/switch` sur le type de transport.

## Décision

Definir cinq ports etroits (SPI) :

- `TaskChannel` — `dispatchTask` / `receiveTask` ;
- `SignalChannel` — `broadcastSignal` / `receiveSignal` ;
- `EventChannel` — `publishEvent` / `subscribe` ;
- `LifecycleChannel` — `publishAgentEvent` / `subscribeAgentEvents` ;
- `ConnectionLifecycle` — `connect` / `disconnect` / `isConnected`.

`ExecutionTransport` devient une **facade composite** de migration
(`extends` les cinq ports) conservee comme type des beans Spring et de
`TransportConfiguration`, afin que le meme bean soit injectable dans tous les
ports. `getType()` est supprime de l'interface et des 5 implementations.

Consommateurs migres :

- `RemoteStepDispatcher` → `TaskChannel` ;
- `TransportExecutionEventHandler` → `EventChannel` ;
- `TaskExecutionPipeline` → `EventChannel` ;
- `TaskAgentRuntime` → `TaskChannel` + `SignalChannel` + `EventChannel` +
  `ConnectionLifecycle` ;
- `TransportAgentRegistration` / `AgentLifecycleIngestion` → `LifecycleChannel` ;
- `RemoteLifecycleDispatcher` → `SignalChannel`.

Les 5 implementations (InMemory, Kafka, RabbitMQ, HTTP, Socket) supportent
toujours l'ensemble des canaux ; la facade reste donc valide. `TransportType`
reste un enum de configuration/documentation, sans methode associee.

## Consequences

- Ajouter une methode a un canal n'impacte que les implementations qui
  declarent ce canal.
- Plus de `getType()` : la selection de transport reste declarative
  (`transport.type`, `@ConditionalOnProperty`).
- La facade pourra etre retiree quand plus aucun bean/config ne l'utilise.

## Preuves

- `mvn test -pl platform-transport,platform-execution-engine,platform-agent-runtime,platform-app` vert.
- `LocalDistributedEquivalenceIT`, `LocalFlowIT`, `LocalModeAllTasksIT`,
  `WebUiApiIT` verts.

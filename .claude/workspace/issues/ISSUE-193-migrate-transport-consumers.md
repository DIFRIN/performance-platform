# ISSUE-193 — Migrate Transport Consumers to Narrow Ports

**PDR** : PDR-052
**Module** : `platform-execution-engine`, `platform-agent-runtime`, `platform-bootstrap`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : ISSUE-192
**Estime** : L (3-6h)

---

## Objectif

Migrer les consommateurs du transport vers les ports étroits dont ils ont besoin,
puis retirer la façade `ExecutionTransport` si plus aucune référence directe (A4).

## Fichiers à Modifier

```
execution/platform-execution-engine/.../RemoteStepDispatcher.java   — dépendre de TaskChannel + EventChannel
execution/platform-execution-engine/.../TransportExecutionEventHandler.java — EventChannel
execution/platform-agent-runtime/.../TaskAgentRuntime.java          — TaskChannel + SignalChannel + ConnectionLifecycle
execution/platform-agent-runtime/.../AgentLifecycleIngestion.java   — LifecycleChannel
execution/platform-agent-runtime/.../TransportAgentRegistration.java — LifecycleChannel
infra/platform-transport/.../ExecutionTransport.java            — retirer la façade si plus de référence
```

## Règles Spécifiques

- Chaque consommateur n'injecte que les canaux nécessaires (principe d'Interface Segregation).
- Les `@ConditionalOnProperty` de transport restent inchangés.

## Critères de Done

- [ ] Consommateurs migrés, façade retirée si possible
- [ ] `mvn test -pl :platform-execution-engine,:platform-agent-runtime,:platform-bootstrap -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-193 → DONE

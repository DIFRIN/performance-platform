# ISSUE-187 — Rewire Agent Config + Delete LocalAgent/DistributedAgentRuntime

**PDR** : PDR-050
**Module** : `platform-agent-runtime`, `platform-app`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-186, ISSUE-177
**Estime** : L (3-6h)

---

## Objectif

Réécrire `AgentRuntimeConfiguration` pour produire un seul `TaskAgentRuntime` dans
les deux modes, et supprimer `LocalAgent` + `DistributedAgentRuntime` (A1).

## Fichiers à Modifier

```
platform-agent-runtime/src/main/java/com/performance/platform/agent/
  ├── local/LocalAgent.java                     — SUPPRIMER
  └── runtime/DistributedAgentRuntime.java      — SUPPRIMER

platform-app/src/main/java/com/performance/platform/app/config/
  └── AgentRuntimeConfiguration.java            — réécrire (un bean TaskAgentRuntime, paramétré par mode)
```

## Règles Spécifiques

- LOCAL : `InMemoryExecutionTransport` + filtre « tous les noms » + `registrationPort=null`.
- DISTRIBUTED/AGENT : transport réel + filtre config-driven + `TransportAgentRegistration`.
- Aucun `if/switch` sur mode : sélection par `@ConditionalOnProperty`/`@ConditionalOnExpression` (CF-03).

## Critères de Done

- [ ] `LocalAgent`/`DistributedAgentRuntime` supprimés
- [ ] `AgentRuntimeConfiguration` réécrite, smoke LOCAL/AGENT verts
- [ ] `mvn test -pl platform-agent-runtime,platform-app -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-187 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (LocalAgent/DistributedAgentRuntime → ❌ REMOVED, TaskAgentRuntime → PLANNED)

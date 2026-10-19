# ISSUE-174 — Fast-Fail on Claim Loss + AgentLossRecoveryIT

**PDR** : PDR-045
**Module** : `platform-execution-engine`
**Statut** : IN_PROGRESS
**Priorité** : P0
**Bloquée par** : ISSUE-171, ISSUE-173
**Estime** : L (3-6h)

---

## Objectif

Faire échouer rapidement un step dont un agent claimer est perdu (B3). Le moteur
écoute `AgentLost` (domaine), appelle `tracker.onAgentLost(agentId)`, rendant
`ALL_COMPLETE` satisfiable, et le step échoue bien avant le timeout 300 s. Ajouter
`AgentLossRecoveryIT` (T4).

## Fichiers à Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/
  └── remote/TransportExecutionEventHandler.java  — ajouter @EventListener AgentLost → tracker.onAgentLost
  └── (ou RemoteExecutionEngine si pré-consolidation) — idem

platform-execution-engine/src/test/java/com/performance/platform/engine/
  └── e2e/AgentLossRecoveryIT.java        — NOUVEAU (T4, Testcontainers ou stub lifecycle)
```

## Règles Spécifiques

- L'écoute de `AgentLost` est un `@EventListener` (ou injection du callback TTL)
  qui appelle `tracker.onAgentLost(agentId)`.
- Un step `ALL_COMPLETE` avec un agent mort → échec << `taskExecutionTimeout` (pas 300 s).
- T4 : tuer un agent en cours de tâche ; assert échec rapide + émission `AgentLost`.

## Critères de Done

- [ ] Fast-fail sur perte d'agent (pas de stall 300 s)
- [ ] `AgentLossRecoveryIT` (T4) vert
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-174 → DONE

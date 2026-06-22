# ADR-009 — Consumer Group Kafka Unique par Agent

**Date** : 2026-06-08
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : Le broadcast Kafka vers des agents spécialisés nécessite que chaque
agent reçoive tous les messages du topic tasks.

---

## Contexte

En Kafka, les messages d'une partition sont distribués entre les membres d'un même
consumer group (load balancing). Si tous les agents partagent `consumerGroup: perf-platform`,
seul l'un d'eux reçoit chaque message — impossible de broadcaster.

## Décision

**Chaque agent utilise son propre consumer group, dont la valeur est son `agentId`.**

```yaml
# Config automatique côté agent
transport:
  kafka:
    consumerGroup: ${agent.id}   # UUID unique par instance
```

Ainsi, chaque agent reçoit tous les messages du topic `agents-tasks` et filtre localement
via `TaskSpecializationFilter`.

## Justification

- **Broadcast natif** : mécanisme standard Kafka pour le pub/sub (multiple consumer groups
  sur le même topic).
- **Zero config** : le consumer group est auto-configuré depuis `agent.id`.
- **Scalabilité** : N agents = N consumer groups indépendants. Chaque groupe comporte
  1 seul membre → pas de rebalance inter-agents.
- **Filtering local** : les messages ignorés sont consommés (offset commité) immédiatement
  après le filtre — pas de dead letter, pas de requeue.

## Conséquences

- Le topic `agents-tasks` doit avoir au moins 1 partition (ordering garanti avec 1 partition).
- Le nombre de consumer groups = nombre d'agents actifs. Pour 100 agents, 100 consumer groups
  sur le même topic — acceptable pour Kafka (pas de limite dure sous ce seuil).
- Les messages ignorés sont consommés silencieusement. Prévoir des métriques
  `tasks.filtered.count` par agent pour l'observabilité (acceptable selon analyse risques).
- `consumerGroup` ne doit PAS être partagé entre agents — documenter explicitement
  dans la config et le guide opérationnel.

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Topic par type de task (`agents-tasks.performance_test`) | N topics à créer/gérer, couplage fort entre DSL et infrastructure Kafka |
| Routing par header Kafka | Nécessite un routeur intermédiaire, complexité sans bénéfice net |
| Consumer group partagé + filtre applicatif | Seul un agent reçoit chaque message — pas de broadcast |

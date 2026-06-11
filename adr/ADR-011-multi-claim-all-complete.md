# ADR-011 — Multi-Claim : L'Orchestrateur Accepte Tous les Claims

**Date** : 2026-06-08
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : Plusieurs agents peuvent être spécialisés pour la même task.
En broadcast, tous peuvent répondre simultanément.

---

## Contexte

Avec le modèle broadcast + filtre, si deux agents A et B sont spécialisés pour
`performance_test`, les deux reçoivent la `TaskExecutionRequest` et publient
`TaskClaimedByAgent`. L'orchestrateur doit décider quoi faire des claims multiples.

## Décision

**L'orchestrateur accepte tous les claims et suit l'exécution de chacun indépendamment.**

Les résultats sont stockés sous `context["taskId"]["agentId"]`.

La complétion de la task dépend de `TaskCompletionPolicy` :
- `FIRST_COMPLETE` : avancer dès le premier résultat reçu (les autres sont stockés si arrivent)
- `ALL_COMPLETE` : attendre tous les agents ayants claimé

La policy est configurable par step ou globalement.

## Justification

- **Pas de perte de données** : chaque résultat est précieux — tests distribués multi-agents.
- **Flexibilité** : `FIRST_COMPLETE` pour les tasks où un seul résultat suffit (préparation),
  `ALL_COMPLETE` pour les injections distribuées (agréger les métriques de N agents).
- **Pas de race condition côté orchestrateur** : pas de "premier claim gagne" à implémenter,
  pas de lock distribué nécessaire.
- **Observable** : chaque claim est tracé dans `TaskCorrelationTracker` avec son agentId.

## Structure du Contexte Résultant

```
context["performance_test"]
  ├── "agent-perf-eu-01"  → InjectionResult(p95=420ms, throughput=850rps)
  ├── "agent-perf-eu-02"  → InjectionResult(p95=380ms, throughput=920rps)
  └── "agent-perf-us-01"  → InjectionResult(p95=510ms, throughput=780rps)
```

Les assertions peuvent alors itérer sur tous les résultats ou utiliser `getFirst()`.

## Conséquences

- `TaskCorrelationTracker` doit gérer `1:N` (un messageId → N claims).
- `ExecutionRepository.saveTaskResult()` est appelé N fois pour la même task.
- Les assertions doivent être adaptées pour lire un résultat multi-agents
  (via `context.getFirst(taskId, type)` ou `context.getAll(taskId)`).
- `ScenarioRestartSignal` doit être envoyé à tous les agents ayant claimé en cas de failure.

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Premier claim gagne, ignorer les suivants | Perte des résultats d'agents valides, lock distribué nécessaire |
| L'orchestrateur choisit un agent avant dispatch | Reintroduit `AgentAllocator` — rejeté par ADR-008 |

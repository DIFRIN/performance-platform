# ADR-010 — PartialExecutionContext comme Contrat de Transmission

**Date** : 2026-06-08
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : Les agents ne doivent recevoir que le sous-ensemble du contexte
dont ils ont besoin, pas l'intégralité de l'ExecutionContext global.

---

## Contexte

L'`ExecutionContext` global contient les résultats de toutes les tasks précédentes.
Transmettre l'intégralité à chaque agent pose trois problèmes :
1. Volume croissant au fil des steps (résultats Gatling, données DB, etc.)
2. Exposition d'informations inutiles à l'agent
3. Couplage implicite entre agents

## Décision

**Un `PartialExecutionContext` est construit par l'orchestrateur pour chaque dispatch.**

Il contient uniquement les entrées dont les clés sont déclarées dans `StepDefinition.requiredContexts`.
Le reste de l'`ExecutionContext` reste côté orchestrateur.

```yaml
# Déclaration dans le scénario
steps:
  - id: inject
    task: performance_test
    requiredContexts:
      - purge-db    # seuls ces deux sous-contextes seront transmis
      - login
```

## Structure du Store

```
ExecutionContext (côté orchestrateur — complet)
  "purge-db"   → { "agent-local": TaskResult }
  "login"      → { "agent-auth-01": TaskResult }
  "inject"     → { "agent-perf-01": TaskResult }

PartialExecutionContext (transmis à l'agent inject)
  "purge-db"   → { "agent-local": TaskResult }
  "login"      → { "agent-auth-01": TaskResult }
  // "inject" absent — non requis
```

## Justification

- **Principe du moindre privilège** : un agent ne voit que ce dont il a besoin.
- **Maîtrise du volume** : la taille du message est bornée par `requiredContexts`.
- **Contrat explicite** : le scénario YAML documente les dépendances de données.
- **Compatibilité** : `PartialExecutionContext` expose les mêmes accesseurs que
  `ExecutionContext` — les `TaskExecutor` existants fonctionnent sans modification.

## Conséquences

- `StepDefinition.requiredContexts` doit être validé au parse time (toutes les clés
  référencées doivent exister comme id d'un step antérieur dans le DAG).
- Un step sans `requiredContexts` reçoit un contexte vide.
- La sérialisation JSON du `PartialExecutionContext` doit être cohérente avec
  la désérialisation côté agent (Jackson, même schéma).
- Les `InjectionResult` complets (avec rawStats) peuvent être volumineux.
  Accepté selon analyse risques (R4 : JVM 2-4 GB, volume acceptable).

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Transmettre l'ExecutionContext complet | Volume croissant, exposition non nécessaire |
| Store externe partagé (Redis, DB) | Complexité infrastructure, hors scope actuel |
| Référence par clé (agent fetch le contexte) | Round-trip réseau supplémentaire, complexité |

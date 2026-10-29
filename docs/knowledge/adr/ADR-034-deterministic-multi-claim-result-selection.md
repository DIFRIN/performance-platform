# ADR-034 — Selection deterministe du resultat multi-claim

**Date** : 2026-10-19
**Statut** : ACCEPTED
**Décideurs** : Architect + implémentation (Claude Code), sur constat de l'audit `improvements.md` (B2)
**Raffine** : ADR-011 (multi-claim `ALL_COMPLETE`)
**Impacte interfaces publiques** : OUI (additif) — `ResultSelectionPolicy`, méthodes de tracker, composant `ExecutionConfig`

---

## Contexte

En multi-claim, plusieurs agents executent le meme step. `RemoteStepDispatcher`
choisissait le resultat avec `results.values().stream().filter(isSuccess).findFirst()`
sur une `ConcurrentHashMap` : l'agent rapporte etait arbitraire. Pour une
plateforme dont la sortie est une mesure, c'est un bug de correction.

## Décision

- Ajouter `ResultSelectionPolicy` (`FIRST_COMPLETED`, `LAST_COMPLETED`) dans
  `platform-use-cases.config`.
- Le tracker enregistre l'ordre atomique des premieres completions
  (`CopyOnWriteArrayList`) et expose :
  - `resultsInCompletionOrder(messageId)` ;
  - `selectResult(messageId, policy)`.
- `ExecutionConfig` porte la politique (defaut `FIRST_COMPLETED`) ; binding via
  `execution.result-selection-policy`.
- `RemoteStepDispatcher` parcourt l'ordre de completion et preferre un resultat
  reussi ; a defaut, retourne le resultat selectionne par la politique.
- `ExecutionConfig` conserve un constructeur 4 arguments pour les appelants
  existants.

## Conséquences

- Deux executions identiques selectionnent le meme agent.
- La selection ne depend jamais de l'ordre d'iteration d'une map.
- T6 : 1 000 iterations verifient `FIRST_COMPLETED` et `LAST_COMPLETED`.

## Preuves

- `ResultSelectionDeterminismTest` (T6) vert.
- `mvn test -pl :platform-use-cases,:platform-execution-engine` vert.

# ADR-029 — Suppression de `TaskExecutorLookup.findAssertionExecutor`

**Date** : 2026-10-19
**Statut** : ACCEPTED
**Décideurs** : Architect + implémentation (Claude Code), sur constat de l'audit `improvements.md` (A2)
**Raffine** : ADR-008 (routing par `taskName`), PDR-035/PDR-037
**Impacte interfaces publiques** : OUI — suppression d'une méthode publique (déjà `@Deprecated(forRemoval=true)`)

---

## Contexte

Depuis ISSUE-150, `AssertionExecutor extends TaskExecutor`. Depuis ISSUE-154,
`LocalStepDispatcher` résout toutes les phases (PREPARATION, INJECTION, ASSERTION)
via `findTaskExecutor(taskName)` — les assertions sont donc déjà résolues par le
chemin unifié.

`TaskExecutorLookup.findAssertionExecutor` restait néanmoins présent comme méthode
`default` dépréciée, et `RegistryTaskExecutorLookup` la surchargeait en déléguant à
un second registre (`AssertionExecutorRegistry`). Deux chemins coexistaient, dont
un mort en production.

## Décision

- Supprimer `TaskExecutorLookup.findAssertionExecutor`.
- Supprimer la surcharge dans `RegistryTaskExecutorLookup` et son paramètre
  `AssertionExecutorRegistry`.
- Migrer les stubs de test (engine + app) vers `findTaskExecutor`.

## Conséquences

- Un seul chemin de résolution : `findTaskExecutor(String)`.
- `platform-app` ne dépend plus de `platform-assertion` pour le lookup.
- Aucun changement de comportement : le chemin `findAssertionExecutor` n'était
  plus appelé par le code principal.

## Preuves

- `mvn test -pl platform-execution-engine,platform-assertion,platform-app` vert.
- `AssertionPipelineE2ETest` résout les 6 assertions via `DefaultTaskExecutorRegistry`.

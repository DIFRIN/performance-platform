# ADR-030 — Suppression de `AssertionExecutorRegistry` et `DefaultAssertionExecutorRegistry`

**Date** : 2026-10-19
**Statut** : ACCEPTED
**Décideurs** : Architect + implémentation (Claude Code), sur constat de l'audit `improvements.md` (A2)
**Raffine** : ADR-029, PDR-035/PDR-036
**Impacte interfaces publiques** : OUI — suppression d'interfaces et d'une exception publiques

---

## Contexte

`AssertionExecutorRegistry` était un miroir de `TaskExecutorRegistry`. Depuis que
`AssertionExecutor extends TaskExecutor`, `DefaultTaskExecutorRegistry` collecte
automatiquement les assertions via `List<TaskExecutor>` : le second registre est
structurellement redondant. La migration ISSUE-152…156 l'avait déprécié sans le
retirer, et le chemin déprécié restait câblé dans `RuntimeAssemblyConfiguration`.

La collision de nom historique (`database`) a été résolue par ISSUE-156 :
`DatabaseAssertionExecutor.getSupportedAssertionName()` retourne
`database-assertion`.

## Décision

- Supprimer `AssertionExecutorRegistry`, `DefaultAssertionExecutorRegistry`,
  `UnsupportedAssertionNameException` et leurs tests.
- `RegistryTaskExecutorLookup` ne dépend plus que de `TaskExecutorRegistry`.
- `RuntimeAssemblyConfiguration` ne câble plus qu'un seul registre.

## Conséquences

- Un registre unique pour toutes les tâches et assertions.
- `platform-assertion` ne dépend plus de `platform-injection-gatling`.
- Les résolutions inconnues lèvent `UnsupportedTaskNameException`.

## Preuves

- `AssertionExecutorAsTaskExecutorTest` vérifie les 6 noms via `DefaultTaskExecutorRegistry`.
- `mvn test -pl :platform-assertion,:platform-execution-engine,:platform-bootstrap` vert.

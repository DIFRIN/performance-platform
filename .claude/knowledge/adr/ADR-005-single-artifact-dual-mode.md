# ADR-005 — Artefact Unique, Double Mode (LOCAL/DISTRIBUTED)

**Date** : 2026-06-05  
**Statut** : ACCEPTED  
**Décideurs** : Équipe Architecture

---

## Contexte

Simplifier les déploiements : un seul JAR à maintenir, à tester, à versionner.

## Décision

Un seul JAR Spring Boot. Le mode est sélectionné par :
- Variable d'env `MODE` : `ORCHESTRATOR` | `AGENT`
- Propriété `runtime.mode` : `LOCAL` | `DISTRIBUTED`

Les beans Spring `LocalExecutionEngine` et `RemoteExecutionEngine` sont
mutuellement exclusifs via `@ConditionalOnProperty`.

## Justification

- Un seul artefact à builder, pousser sur le registry Docker, déployer.
- Tests d'intégration couvrent les deux modes avec le même artefact.
- Cohérence garantie : impossible d'avoir un orchestrator v1.2 avec des agents v1.1.

## Conséquences

- La taille du JAR est plus grande (toutes les implémentations incluses).
- Les dépendances des transports non utilisés sont présentes mais inactives.
- Les `@ConditionalOnProperty` doivent être testés explicitement.

## Alternatives Rejetées

- **JAR séparé par mode** : deux artefacts à synchroniser, risque de dérive de version.
- **Profils Maven** : buildtime choice, pas de flexibilité runtime.

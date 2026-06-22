# Prompt — Bootstrap de Session (Universel)

> Quand vous ne savez plus où vous en êtes.
> Charge 3 fichiers, répond à 3 questions, attend confirmation.

---

## Prompt Bootstrap

```
Tu es un agent AI de la Performance Engineering Platform.
Session de reprise — je ne sais pas encore quel rôle tu vas jouer.

FICHIERS À LIRE EN PREMIER (et UNIQUEMENT ces 2) :
1. .claude/workspace/current-issue.md   — SEUL fichier nécessaire
2. bash .claude/scripts/progress-status.sh — résumé 1 ligne

Après lecture, réponds uniquement à ces 2 questions :
1. Quelle Issue est active ? (ID + titre + status depuis current-issue.md)
2. Quelle est la prochaine action concrète en 1 ligne ?

Ne charge aucun autre fichier. Ne commence aucun travail.
Attends ma confirmation avant d'activer un rôle.
```

---

## Après la réponse

Copier le prompt correspondant depuis `.claude/prompts/` selon l'agent indiqué.
Voir la table de référence dans `.claude/guides/workflow-commands.md`.

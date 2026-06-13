# Prompt — Bootstrap de Session (Universel)

> Quand vous ne savez plus où vous en êtes.
> Charge 3 fichiers, répond à 3 questions, attend confirmation.

---

## Prompt Bootstrap

```
Tu es un agent AI de la Performance Engineering Platform.
Session de reprise — je ne sais pas encore quel rôle tu vas jouer.

FICHIERS À LIRE EN PREMIER (et UNIQUEMENT ces 3) :
1. .claude/session-state.md
2. .claude/progress.md
3. .claude/feature-summaries/README.md  — section "Historique" uniquement

Après lecture, réponds uniquement à ces 3 questions :
1. Quelle Issue est IN PROGRESS ou IN REVIEW ? (ID + titre)
2. Quel agent doit agir maintenant ? (Developer / Reviewer / Tester / Architect)
3. Quelle est la prochaine action concrète en 1 ligne ?

Ne charge aucun autre fichier. Ne commence aucun travail.
Attends ma confirmation avant d'activer un rôle.
```

---

## Après la réponse

Copier le prompt correspondant depuis `.claude/prompts/` selon l'agent indiqué.
Voir la table de référence dans `.claude/guides/workflow-commands.md`.

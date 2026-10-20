# ISSUE-168 — CI Pipeline GitHub Actions

**PDR** : PDR-043
**Module** : root (`.github/workflows`)
**Statut** : DONE
**Priorité** : P0 (bloquant — sans CI, rien ne reste fixé)
**Bloquée par** : —
**Estime** : S (< 1h)

---

## Objectif

Créer le pipeline CI qui exécute la suite complète à chaque push/PR (T9, D1).
2 400+ tests qui ne tournent automatiquement nulle part aujourd'hui.

## Fichiers à Créer

```
.github/workflows/
  └── ci.yml          — job: mvn -B verify -P integration-tests (Testcontainers disponibles)
```

## Règles Spécifiques

- Le job exécute `mvn -B verify -P integration-tests` (unit + integration).
- Testcontainers dispo : runner `ubuntu-latest` avec Docker.
- Aucun changement de pom ni de code de production.

## Critères de Done

- [ ] `.github/workflows/ci.yml` existe et exécute `mvn verify -P integration-tests`
- [ ] Le pipeline est déclenché sur `push` et `pull_request`
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-168 → DONE

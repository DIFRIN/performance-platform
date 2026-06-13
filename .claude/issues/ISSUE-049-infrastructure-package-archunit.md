# ISSUE-049 — ArchUnit : séparation stricte des packages infrastructure

**PDR** : PDR-011
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-039, ISSUE-046
**Estime** : S

---

## Objectif

Créer un test ArchUnit garantissant la séparation stricte des 4 sous-packages de
`platform-infrastructure` : `executor`, `plugin`, `persistence`, `publisher`. Aucun ne doit
dépendre cycliquement d'un autre ; chaque responsabilité reste confinée.

## Fichiers à Créer

```
platform-infrastructure/src/test/java/com/performance/platform/infrastructure/arch/
  └── InfrastructurePackageSeparationTest.java
```

## Règles à Vérifier (ArchUnit)

- `..persistence..` ne dépend PAS de `..executor..`, `..plugin..`, `..publisher..`.
- `..publisher..` ne dépend PAS de `..executor..`, `..persistence..`, `..plugin..`.
- `..executor..` ne dépend PAS de `..persistence..`, `..publisher..`.
- `..plugin..` peut dépendre de `..executor..` (fusion des executors) mais PAS de `persistence`/`publisher`.
- Aucune dépendance cyclique entre slices.
- Annotations JPA confinées à `..persistence..`.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Test ArchUnit passe avec les 4 packages présents
- [ ] `.claude/progress.md` mis à jour : ISSUE-049 → DONE

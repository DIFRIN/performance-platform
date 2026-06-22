# ISSUE-XXX: Titre de l'Issue

<!--
  FORMAT OBLIGATOIRE du heading : "# ISSUE-XXX: Titre"
  - Le séparateur DOIT être ":" (deux-points) pour que les scripts issue-*.sh
    puissent extraire le titre avec le pattern sed.
  - Alternative tolérée : "# ISSUE-XXX — Titre" (em-dash) — mais ":" est canonique.
  - Les scripts utilisent :
      TITLE=$(grep '^# ' "$ISSUE_FILE" | head -1 | sed -E 's/^# [A-Z0-9-]+[[:space:]]*[-–—:][[:space:]]*//')
-->

**PDR** : PDR-XXX
**Module** : `platform-xxx`
**Statut** : WAITING
**Priorité** : P0 | P1 | P2
**Bloquée par** : ISSUE-YYY | —
**Taille** : S | M | L
**Estime** : S | M | L

<!--
  CHAMPS UTILISÉS PAR LES SCRIPTS :
  - **PDR**   → issue-start.sh (grep -oP '\*\*PDR\*\*[[:space:]]*:[[:space:]]*\K[^\s`]+')
  - **Module** → issue-start.sh (grep -oP 'platform-[a-z-]+')
  - **Taille** → commit-done-issues.sh (issue_field "Taille")
  - **Statut** → non extrait par les scripts (géré via progress.md)
  - **Bloquée par** → informatif uniquement (les dépendances sont dans progress.md)
  - **Priorité** → informatif uniquement
-->

---

## Objectif

[Ce que cette Issue produit. Ce que le Developer peut vérifier à la fin. 1-2 phrases.]

---

## Fichiers à Créer / Modifier

```
platform-xxx/src/main/java/com/performance/platform/xxx/
  ├── NomClasse.java       — [rôle en 1 ligne]
  └── AutreClasse.java     — [rôle en 1 ligne]

platform-xxx/src/test/java/com/performance/platform/xxx/
  └── NomClasseTest.java   — [ce qui est testé]
```

---

## Interfaces à Implémenter

```java
// Copié depuis le PDR parent — signatures exactes
```

---

## Règles Spécifiques

- [Règle si nécessaire]

---

## Critères de Done

- [ ] `mvn test -pl platform-xxx -q` → 0 erreur, 0 warning
- [ ] [Critère métier 1]
- [ ] [Critère métier 2]
- [ ] `.claude/workspace/progress.md` : ISSUE-XXX → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour
- [ ] `.claude/workspace/session-state.md` mis à jour

# Progress

> Tracker central de tous les PDRs et Issues du projet.
> **Source de vérité unique** pour l'avancement du développement.
>
> Lu EN PREMIER par le Developer à chaque nouvelle session.
> Mis à jour par : System Designer (création), Developer (statuts), Reviewer (validation DONE).
>
> Workflow de statut :
>   WAITING → IN PROGRESS → IN REVIEW → DONE
>   WAITING → BLOCKED (si dépendance non satisfaite)

---

## Démarrage de Session Developer — Protocole

```
1. Lire ce fichier (section PDRs + Issues uniquement — pas les descriptions)
2. Chercher dans cet ordre :
   a. Y a-t-il une Issue IN PROGRESS ?
      → Oui : reprendre cette issue (lire issues/ISSUE-XXX.md + session-state.md)
      → Non : continuer
   b. Y a-t-il une Issue WAITING dont toutes les dépendances sont DONE ?
      → Oui : prendre la première P0, puis P1, puis P2
      → Non : continuer
   c. Toutes les Issues sont DONE ?
      → Informer l'humain : projet terminé ou nouvelles specs nécessaires
3. Marquer l'Issue choisie IN PROGRESS dans ce fichier
4. Lire issues/ISSUE-XXX.md pour le détail
```

---

## Vue d'Ensemble

> Statuts dérivés par lecture des tableaux PDRs et Issues ci-dessous.
> Ne pas maintenir de compteurs — trop fragile manuellement.
> Pour un décompte rapide : `grep -c "WAITING\|IN PROGRESS\|DONE" progress.md`

---

## PDRs

> Listés par ordre de construction. Un PDR ne peut démarrer que si ses dépendances sont DONE.

| ID | Nom | Module | Statut | Issues | Dépend de |
|---|---|---|---|---|---|
| — | _À créer par le System Designer_ | — | — | — | — |

---

## Issues

> Listées par priorité puis par ordre de construction.
> Le Developer prend toujours la première IN PROGRESS, sinon la première WAITING débloquée.

### 🔴 P0 — Bloquantes

| ID | Titre | PDR | Taille | Statut | Bloquée par |
|---|---|---|---|---|---|
| — | _À créer par le System Designer_ | — | — | — | — |

### 🟠 P1 — Critiques

| ID | Titre | PDR | Taille | Statut | Bloquée par |
|---|---|---|---|---|---|
| — | _À créer par le System Designer_ | — | — | — | — |

### 🟡 P2 — Normales

| ID | Titre | PDR | Taille | Statut | Bloquée par |
|---|---|---|---|---|---|
| — | _À créer par le System Designer_ | — | — | — | — |

---

## Historique des Changements de Statut

> Chaque changement de statut est loggé ici. Format : `[date] ISSUE-XXX : ANCIEN → NOUVEAU (agent)`

| Date | Item | Transition | Agent |
|---|---|---|---|
| — | — | — | — |

---

## Métriques

**Démarrage** : [date du premier PDR créé par le System Designer]
**Issues totales** : [rempli par le System Designer à la création]
**Dernière mise à jour** : [date]

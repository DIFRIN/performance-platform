# PDR-062 — Injection Docs & ADR Acceptance

**Module Maven** : `docs/knowledge` (+ distribution/example)
**Package** : `—`
**Statut** : WAITING
**Specs de référence** : `docs/knowledge/adr/` ADR-064..070, ADR-061
**Dépend de** : PDR-055, PDR-056, PDR-057, PDR-058, PDR-059, PDR-060, PDR-061
**Issues** : ISSUE-244

---

## Responsabilité

Aligne la documentation de référence sur l'état livré : `architecture.md`, specs, glossaire, exemples,
table de routing. Passe les ADR-064..070 à ACCEPTED (décision Architect, ADR-061).

Ne fait PAS : modification de code.

---

## Interfaces Publiques

> Signatures exactes. Ce qui est écrit ici prime sur l'ADR en cas de différence.

```java
// Aucune interface.
```

---

## Règles de Comportement

- Un ADR ne passe ACCEPTED qu'avec la mise à jour correspondante de `architecture.md` (ADR-061).
- `CLAUDE.md` n'est modifié que sur décision explicite de l'Architect.

---

## Issues

| ID | Titre | Module | Priorité | Taille | Bloquée par |
|---|---|---|---|---|---|
| ISSUE-244 | Documentation, exemples et acceptation des ADR | `docs` | P2 | M | ISSUE-203, ISSUE-210, ISSUE-211, ISSUE-214, ISSUE-220, ISSUE-225, ISSUE-226, ISSUE-237, ISSUE-243, ISSUE-209, ISSUE-239, ISSUE-241, ISSUE-229, ISSUE-230, ISSUE-222 |

---

## Dépendances Techniques

```
Ce PDR utilise     : PDR-055, PDR-056, PDR-057, PDR-058, PDR-059, PDR-060, PDR-061
Ce PDR est utilisé par : —
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Les interfaces sont dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] Les tests d'intégration (si applicable) passent

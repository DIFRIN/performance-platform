# ISSUE-243 — Métriques d'assertion par requête

**PDR** : PDR-061
**Module** : `platform-assertion`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : ISSUE-242, ISSUE-219
**Estime** : M (1-3h)

---

## Objectif

Permettre de cibler une requête nommée : `injection.latency.p95[create]`.

> Référence : ADR-070.

## Fichiers à Créer / Modifier

```
performance/platform-assertion/src/main/java/com/performance/platform/assertion/ — résolution du suffixe `[name]` + agrégation par nom (modification)
(+ tests)
```

## Interfaces à Implémenter

> Signatures dans `PDR-061` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Sans suffixe = total (comportement actuel).
- Nom inconnu dans les résultats => `INCONCLUSIVE`.
- La validation du nom contre `requests` au parsing est hors périmètre (signaler si souhaitée).

## Critères de Done

- [ ] `mvn test -pl :platform-assertion -q` → 0 erreur
- [ ] p95 d'une requête nommée calculé par fusion multi-agents
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-243 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

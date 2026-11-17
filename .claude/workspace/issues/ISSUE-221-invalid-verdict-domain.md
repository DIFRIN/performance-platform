# ISSUE-221 — Verdict INVALID et famille INTEGRITY

**PDR** : PDR-059
**Module** : `platform-domain`
**Statut** : DONE
**Priorité** : P0
**Bloquée par** : ISSUE-216
**Estime** : S (< 1h)

---

## Objectif

Ajouter `Verdict.INVALID`, `AssertionFamily.INTEGRITY` et `IntegrityTolerances`.

> Référence : ADR-069.

## Fichiers à Créer / Modifier

```
core/platform-domain/src/main/java/com/performance/platform/domain/report/Verdict.java            — + INVALID (modification)
core/platform-domain/src/main/java/com/performance/platform/domain/assertion/AssertionFamily.java — + INTEGRITY (modification)
core/platform-domain/src/main/java/com/performance/platform/domain/assertion/IntegrityTolerances.java — (création)
core/platform-domain/src/test/java/com/performance/platform/domain/assertion/IntegrityTolerancesTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-059` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Tolérances : pourcentages dans ]0,100].
- Vérifier la compilation globale (`switch` exhaustifs sur `Verdict`) et signaler les modules impactés.

## Critères de Done

- [ ] `mvn test -pl :platform-domain -q` → 0 erreur
- [ ] Build complet compile
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-221 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

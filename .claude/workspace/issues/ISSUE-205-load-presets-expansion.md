# ISSUE-205 — Presets : expansion des 8 types

**PDR** : PDR-056
**Module** : `platform-domain`
**Statut** : DONE
**Priorité** : P0
**Bloquée par** : ISSUE-204
**Estime** : M (1-3h)

---

## Objectif

`LoadPresets.expand` convertit chaque type existant en `WorkloadPlan` équivalent.

> Référence : ADR-064.

## Fichiers à Créer / Modifier

```
core/platform-domain/src/main/java/com/performance/platform/domain/injection/
  └── LoadPresets.java
core/platform-domain/src/test/java/com/performance/platform/domain/injection/
  └── LoadPresetsTest.java          — un test par type
```

## Interfaces à Implémenter

> Signatures dans `PDR-056` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Reprendre les clés de paramètres et la logique de `DefaultLoadModelTranslator` (y compris `CUSTOM` : un `Ramp` par paire de points).
- Type inconnu ou paramètre manquant => `IllegalArgumentException` avec le nom du paramètre.
- Modèle `OPEN`, scope `PER_AGENT`, toutes les étapes `measured=true`.

## Critères de Done

- [ ] `mvn test -pl :platform-domain -q` → 0 erreur
- [ ] Les 8 types produisent des étapes équivalentes à la traduction actuelle (mêmes débits, mêmes durées)
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-205 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

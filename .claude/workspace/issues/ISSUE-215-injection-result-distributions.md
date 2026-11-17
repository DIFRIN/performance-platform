# ISSUE-215 — Histogramme, fenêtres et santé dans InjectionResult

**PDR** : PDR-058
**Module** : `platform-domain`
**Statut** : DONE
**Priorité** : P0
**Bloquée par** : ISSUE-204
**Estime** : M (1-3h)

---

## Objectif

Ajouter `LatencyHistogram`, `LoadWindow`, `GeneratorHealth` et étendre `InjectionResult`.

> Référence : ADR-068.

## Fichiers à Créer / Modifier

```
core/platform-domain/src/main/java/com/performance/platform/domain/injection/
  ├── LatencyHistogram.java
  ├── LoadWindow.java
  ├── GeneratorHealth.java
  └── InjectionResult.java          — nouveaux composants (modification)
core/platform-domain/src/test/java/com/performance/platform/domain/injection/
  ├── LatencyHistogramTest.java     — bucketOf, merge, quantile
  └── LoadWindowTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-058` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Ajout de composant à un record public : conserver un constructeur secondaire avec l'ancienne signature (valeur par défaut documentée) pour que les autres modules compilent sans modification dans cette Issue.
- `bucketOf` : identité jusqu'à 1000 ms ; au-delà, arrondi supérieur à 2 chiffres significatifs.
- `merge` associatif et commutatif ; `quantile` = plus petite borne dont le cumul atteint le rang.
- Ajouter `toMap()` / `fromMap()` sur les trois records (transport dans `TaskResult.outputs`).

## Critères de Done

- [ ] `mvn test -pl :platform-domain -q` → 0 erreur
- [ ] `merge(a,b).quantile(q)` égal au quantile calculé sur l'union des échantillons bucketisés
- [ ] Compatibilité : ancien constructeur d'`InjectionResult` toujours utilisable
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-215 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

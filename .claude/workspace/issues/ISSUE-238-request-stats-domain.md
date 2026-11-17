# ISSUE-238 — RequestStats et résultats par requête

**PDR** : PDR-061
**Module** : `platform-domain`
**Statut** : APPROVED
**Priorité** : P2
**Bloquée par** : ISSUE-215
**Estime** : S (< 1h)

---

## Objectif

Ajouter le record `RequestStats` et le composant `perRequest` à `InjectionResult`.

> Référence : ADR-070.

## Fichiers à Créer / Modifier

```
core/platform-domain/src/main/java/com/performance/platform/domain/injection/
  ├── RequestStats.java             — (création)
  └── InjectionResult.java          — + Map<String, RequestStats> perRequest (modification)
core/platform-domain/src/test/java/com/performance/platform/domain/injection/RequestStatsTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-061` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Ajout de composant à un record public : conserver un constructeur secondaire avec l'ancienne signature (valeur par défaut documentée) pour que les autres modules compilent sans modification dans cette Issue.
- `perRequest` vide par défaut ; `Map.copyOf`.
- `toMap()` / `fromMap()` sur `RequestStats`.

## Critères de Done

- [ ] `mvn test -pl :platform-domain -q` → 0 erreur
- [ ] Ancien constructeur d'`InjectionResult` toujours utilisable
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-238 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

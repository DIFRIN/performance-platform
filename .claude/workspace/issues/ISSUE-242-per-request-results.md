# ISSUE-242 — Résultats par nom de requête

**PDR** : PDR-061
**Module** : `platform-injection-gatling`
**Statut** : APPROVED
**Priorité** : P2
**Bloquée par** : ISSUE-240, ISSUE-218, ISSUE-238
**Estime** : M (1-3h)

---

## Objectif

Le parser produit compteurs et histogramme par nom de requête, en plus du total.

> Référence : ADR-070.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/result/SimulationLogResultParser.java — agrégats par nom (modification)
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/GatlingTaskExecutor.java — sortie `perRequest` (modification)
(+ tests)
```

## Interfaces à Implémenter

> Signatures dans `PDR-061` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Sortie `perRequest` : `{name: {requests, failures, histogram}}` sur fenêtres mesurées.
- Total inchangé.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Somme des `requests` par nom = total
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-242 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

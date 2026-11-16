# ISSUE-217 — Parser : histogramme et fenêtres

**PDR** : PDR-058
**Module** : `platform-injection-gatling`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-215, ISSUE-208
**Estime** : L (3-6h)

---

## Objectif

`SimulationLogResultParser` produit en une passe l'histogramme, les fenêtres (requêtes, échecs, utilisateurs démarrés) et les arrivées planifiées par fenêtre.

> Référence : ADR-068.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/result/
  ├── SimulationLogResultParser.java — histogramme + fenêtres (modification)
  └── PlannedArrivals.java          — intégrale du plan par fenêtre (création)
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/result/
  ├── SimulationLogWindowsTest.java
  └── PlannedArrivalsTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-058` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Fenêtres alignées sur l'horloge murale, taille `injection.window` (défaut 5s, env `INJECTION_WINDOW`).
- `plannedArrivals` : intégrale du débit du plan sur la fenêtre (modèle ouvert) ; modèle fermé = 0 et `arrivalKnown=false`.
- `measured` d'une fenêtre = `measured` de l'étape couvrant son début.
- `startedUsers` compté depuis les enregistrements USER START du log.
- Les percentiles scalaires existants restent calculés (sur fenêtres mesurées uniquement).

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Somme des `plannedArrivals` = total du plan (±1)
- [ ] Requêtes des étapes `measured=false` exclues des percentiles
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-217 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

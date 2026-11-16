# ISSUE-209 — Modèle fermé (utilisateurs concurrents)

**PDR** : PDR-056
**Module** : `platform-injection-gatling`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-208
**Estime** : M (1-3h)

---

## Objectif

Supporter `model: closed` : `toClosedSteps` et branchement de la simulation générée sur l'injection fermée.

> Référence : ADR-064.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/load/DefaultWorkloadPlanTranslator.java — toClosedSteps (modification)
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/http/HttpScenarioSimulation.java — injectOpen / injectClosed selon le modèle (modification)
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/kafka/ — idem simulation Kafka (modification)
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/load/ClosedModelTranslatorTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-056` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Ramp→`rampConcurrentUsers`, Hold→`constantConcurrentUsers`, Pause→palier à 0.
- En modèle fermé chaque utilisateur boucle sur la requête pendant la durée du plan.
- Les niveaux sont arrondis à l'entier le plus proche ; `Burst` déjà rejeté à la validation.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Un plan fermé de test s'exécute et maintient la population demandée
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-209 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

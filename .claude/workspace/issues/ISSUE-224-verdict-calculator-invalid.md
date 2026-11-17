# ISSUE-224 — Verdict INVALID dans le moteur de rapport

**PDR** : PDR-059
**Module** : `platform-reporting`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-223
**Estime** : M (1-3h)

---

## Objectif

Le moteur de rapport génère les contrôles d'intégrité pour chaque étape d'injection et `VerdictCalculator` applique la priorité INVALID > FAILED > WARNING > SUCCESS.

> Référence : ADR-069.

## Fichiers à Créer / Modifier

```
performance/platform-reporting/src/main/java/com/performance/platform/reporting/engine/
  ├── VerdictCalculator.java        — priorité + INVALID (modification)
  ├── PerformanceAssessmentBuilder.java — contrôles d'intégrité en tête (modification)
  └── DefaultReportEngine.java      — appel d'IntegrityCheckFactory (modification)
performance/platform-reporting/src/test/java/com/performance/platform/reporting/engine/VerdictCalculatorInvalidTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-059` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Contrôle `INTEGRITY` FAILED (hors saturation) => `INVALID`, quelles que soient les autres assertions.
- Tolérances lues depuis `step.parameters.integrity`, sinon `IntegrityTolerances.defaults()`.
- Limitation ajoutée au rapport : « Résultats non concluants : la charge décrite n'a pas été appliquée ».

## Critères de Done

- [ ] `mvn test -pl :platform-reporting -q` → 0 erreur
- [ ] Run sous-livré avec assertions vertes => INVALID
- [ ] Run nominal => verdict inchangé
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-224 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

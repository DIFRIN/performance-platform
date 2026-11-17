# ISSUE-225 — Rendu HTML/PDF du verdict INVALID

**PDR** : PDR-059
**Module** : `platform-report-renderers`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : ISSUE-224
**Estime** : S (< 1h)

---

## Objectif

Afficher la bannière INVALID et les contrôles d'intégrité en tête du rapport.

> Référence : ADR-069.

## Fichiers à Créer / Modifier

```
infra/platform-report-renderers/src/main/java/com/performance/platform/reporting/render/
  └── HtmlReportRenderer.java       — bannière + section intégrité (modification)
(+ test de rendu existant étendu)
```

## Interfaces à Implémenter

> Signatures dans `PDR-059` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Couleur distincte de FAILED ; libellé « INVALID — test non valide ».
- Résultats d'assertion affichés mais marqués « non concluants ».

## Critères de Done

- [ ] `mvn test -pl :platform-report-renderers -q` → 0 erreur
- [ ] Rendu HTML et PDF contiennent la bannière pour un rapport INVALID
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-225 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

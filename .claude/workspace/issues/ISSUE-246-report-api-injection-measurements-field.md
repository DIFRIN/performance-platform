# ISSUE-246 — CampaignReport : mesures d'injection (forme neutre)

**PDR** : PDR-058
**Module** : `platform-report-api`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-219
**Estime** : S (< 1h)

---

## Objectif

Donner à `CampaignReport` un champ neutre pour la section « mesures d'injection » (ADR-068 §7) et ses preuves (ADR-051), afin que `platform-reporting` (ISSUE-220) puisse le renseigner et que le JSON le sorte. Débloque ISSUE-220.

## Fichiers à Créer / Modifier

```
core/platform-report-api/src/main/java/com/performance/platform/reporting/model/
  └── CampaignReport.java           — + injectionMeasurements (modification)
core/platform-report-api/src/test/java/com/performance/platform/reporting/model/
  └── CampaignReportTest.java       — nouveau champ, défaut, immutabilité
```

## Interfaces à Implémenter

```java
public record CampaignReport(
    /* 16 composants existants, inchangés */
    PerformanceAssessment performance,
    Map<String, Object> injectionMeasurements   // forme canonique (Map/List/Number/String) ; vide = non calculé
) {
    // constructeurs existants CONSERVÉS (15 args, 16 args avec performance) : délèguent avec Map.of()
    public boolean hasInjectionMeasurements();  // !injectionMeasurements.isEmpty()
}
```

## Règles Spécifiques

- Les constructeurs à 15 et 16 arguments restent valides : aucun appelant existant (`DefaultReportEngine`, tests de `platform-report-renderers`) ne doit être modifié pour compiler.
- Compact constructor : `injectionMeasurements = injectionMeasurements == null ? Map.of() : Map.copyOf(injectionMeasurements)`.
- Pas de type `InjectionMeasurementsSection` ni de type domaine d'injection ici : la section vit dans `platform-reporting`, qui dépend de `platform-report-api` (pas l'inverse). Une `Map` canonique se sérialise telle quelle en JSON.
- Aucune annotation Spring/Jackson. Ne pas toucher au rendu HTML/PDF (hors périmètre).

## Critères de Done

- [ ] `mvn test -pl :platform-report-api -am -q` → 0 erreur
- [ ] `mvn compile -q` sur tout le projet : aucun autre module modifié
- [ ] `new CampaignReport(...)` (15 et 16 args) donne `hasInjectionMeasurements() == false`
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-246 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` : `CampaignReport` (étendu) ajouté

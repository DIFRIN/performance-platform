# ISSUE-245 — InjectionReportEntry : plan résolu et hash

**PDR** : PDR-056
**Module** : `platform-report-api`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-208
**Estime** : S (< 1h)

---

## Objectif

Ajouter à `InjectionReportEntry` le plan de charge résolu et son hash, pour que `platform-reporting` (ISSUE-211) puisse les renseigner et qu'ils sortent dans le JSON du rapport.

> Référence : ADR-064 §6.

## Fichiers à Créer / Modifier

```
core/platform-report-api/src/main/java/com/performance/platform/reporting/model/
  └── InjectionReportEntry.java     — + workloadPlan, workloadPlanHash (modification)
core/platform-report-api/src/test/java/com/performance/platform/reporting/model/
  ├── InjectionReportEntryTest.java — nouveaux champs, défauts, immutabilité
  └── CampaignReportTest.java       — vérifier que les constructeurs existants compilent (modification si besoin)
```

## Interfaces à Implémenter

```java
public record InjectionReportEntry(
    TaskId taskId,
    InjectionResult metrics,
    Path gatlingReportDirectory,
    String agentId,                         // nullable (existant)
    Map<String, Object> workloadPlan,       // forme canonique WorkloadPlanCodec.toMap ; vide = plan non enregistré
    String workloadPlanHash                 // SHA-256 hex ; "" = non enregistré
) {
    // constructeurs existants CONSERVÉS :
    public InjectionReportEntry(TaskId taskId, InjectionResult metrics, Path gatlingReportDirectory);
    public InjectionReportEntry(TaskId taskId, InjectionResult metrics, Path gatlingReportDirectory, String agentId);

    public boolean planRecorded();          // !workloadPlanHash.isBlank()
}
```

## Règles Spécifiques

- Les deux constructeurs existants restent valides et délèguent avec `Map.of()` et `""` : aucun autre module (`platform-reporting`, `platform-report-renderers`, tests) ne doit nécessiter de modification pour compiler.
- Compact constructor : `workloadPlan = workloadPlan == null ? Map.of() : Map.copyOf(workloadPlan)` ; `workloadPlanHash = workloadPlanHash == null ? "" : workloadPlanHash`.
- Pas de type `WorkloadPlan` dans ce record : une `Map` canonique évite d'imposer le domaine d'injection au contrat de rapport et se sérialise telle quelle en JSON (Jackson, `JsonReportRenderer`).
- Aucune annotation Spring/Jackson dans `platform-report-api`.
- Ne pas toucher au rendu HTML/PDF : hors périmètre (module `platform-report-renderers`).

## Critères de Done

- [ ] `mvn test -pl :platform-report-api -q` → 0 erreur
- [ ] `mvn compile -q` sur tout le projet : aucun autre module n'a dû être modifié
- [ ] `new InjectionReportEntry(taskId, metrics, dir)` donne `planRecorded() == false`
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-245 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` : `InjectionReportEntry` (étendu) ajouté

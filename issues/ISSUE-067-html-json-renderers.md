# ISSUE-067 — HtmlReportRenderer + JsonReportRenderer

**PDR** : PDR-015
**Module** : `platform-reporting`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-065
**Estime** : M

---

## Objectif

Implémenter les renderers HTML (template) et JSON (Jackson).

## Fichiers à Créer

```
platform-reporting/src/main/java/com/performance/platform/reporting/render/
  ├── HtmlReportRenderer.java
  └── JsonReportRenderer.java
platform-reporting/src/main/resources/templates/
  └── campaign-report.html   — template (en-tête, résumé, sections, verdict badge)

platform-reporting/src/test/java/com/performance/platform/reporting/render/
  ├── HtmlReportRendererTest.java
  └── JsonReportRendererTest.java — round-trip JSON
```

## Interfaces à Implémenter

```java
@Component public class HtmlReportRenderer implements ReportRenderer { public ReportFormat getFormat() { return ReportFormat.HTML; } }
@Component public class JsonReportRenderer implements ReportRenderer { public ReportFormat getFormat() { return ReportFormat.JSON; } }
```

## Règles Spécifiques

- HTML : sections En-tête / Résumé / Préparation / Injection (KPIs Gatling) / Assertions / Footer ; verdict en badge coloré.
- JSON : sérialisation complète de `CampaignReport` (Jackson, JSR-310).
- Pas d'I/O fichier ici — `render()` retourne des `byte[]`.

## Critères de Done

- [ ] `mvn test -pl platform-reporting -q` → 0 erreur
- [ ] HTML contient le verdict et les KPIs ; JSON désérialisable en `CampaignReport`
- [ ] `progress.md` mis à jour : ISSUE-067 → DONE
- [ ] `context/interfaces-registry.md` mis à jour

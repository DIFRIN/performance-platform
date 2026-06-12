# ISSUE-068 — PdfReportRenderer (OpenHTMLToPDF)

**PDR** : PDR-015
**Module** : `platform-reporting`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-067
**Estime** : S

---

## Objectif

Implémenter `PdfReportRenderer` qui convertit le HTML du rapport en PDF via OpenHTMLToPDF.

## Fichiers à Créer

```
platform-reporting/src/main/java/com/performance/platform/reporting/render/
  └── PdfReportRenderer.java

platform-reporting/src/test/java/com/performance/platform/reporting/render/
  └── PdfReportRendererTest.java — magic bytes %PDF
```

## Interfaces à Implémenter

```java
@Component
public class PdfReportRenderer implements ReportRenderer {
    public PdfReportRenderer(HtmlReportRenderer htmlRenderer) { /* ... */ }
    public byte[] render(CampaignReport report) throws RenderException { /* HTML → PDF */ }
    public ReportFormat getFormat() { return ReportFormat.PDF; }
}
```

## Règles Spécifiques

- Réutilise `HtmlReportRenderer` puis convertit (com.openhtmltopdf:openhtmltopdf-pdfbox).
- Dépendance Maven justifiée dans le pom (CC-03).

## Critères de Done

- [ ] `mvn test -pl platform-reporting -q` → 0 erreur
- [ ] Le `byte[]` commence par `%PDF`
- [ ] `progress.md` mis à jour : ISSUE-068 → DONE
- [ ] `context/interfaces-registry.md` mis à jour

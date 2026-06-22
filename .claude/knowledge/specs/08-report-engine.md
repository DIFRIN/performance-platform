# Spec 08 — Report Engine

**Module** : `platform-reporting`  
**Dépend de** : `platform-domain`

---

## 1. Objectif

Générer un rapport complet de campagne (HTML, PDF, JSON) et le publier
vers les destinations configurées.

---

## 2. Interfaces

```java
public interface ReportEngine {
    CampaignReport generate(ExecutionState state) throws ReportGenerationException;
}

public interface ReportPublisher {
    void publish(CampaignReport report, PublisherConfig config) throws PublicationException;
    PublicationTarget getTarget();
}

public interface ReportRenderer {
    byte[] render(CampaignReport report) throws RenderException;
    ReportFormat getFormat();          // HTML | PDF | JSON
}
```

---

## 3. Structure du Rapport

```java
public record CampaignReport(
    ReportId id,
    ScenarioId scenarioId,
    String scenarioName,
    String scenarioVersion,
    List<String> tags,
    Map<String, String> metadata,

    EnvironmentInfo environment,
    ExecutionSummary executionSummary,

    List<TaskReportEntry> preparationResults,
    List<InjectionReportEntry> injectionResults,
    List<AssertionReportEntry> assertionResults,

    Verdict verdict,
    String verdictReason,

    Instant generatedAt,
    Duration totalDuration
) {}

public record EnvironmentInfo(
    List<String> agentIds,
    String jvmVersion,
    Map<String, String> systemProperties
) {}

public record ExecutionSummary(
    int totalTasks,
    int successfulTasks,
    int failedTasks,
    int skippedTasks,
    Duration preparationDuration,
    Duration injectionDuration,
    Duration assertionDuration
) {}

public record InjectionReportEntry(
    TaskId taskId,
    InjectionResult metrics,
    Path gatlingReportDirectory    // embarqué dans le rapport HTML
) {}

public record AssertionReportEntry(
    TaskId assertionId,
    AssertionResult result,
    Evidence evidence
) {}
```

---

## 4. Formats de Sortie

### HTML
Généré avec OpenHTMLToPDF ou Thymeleaf → HTML statique.  
Structure :
- En-tête : logo, metadata, verdict (badge coloré)
- Résumé exécution : timeline, KPIs globaux
- Section Préparation : tableau des tâches
- Section Injection : KPIs Gatling, graphiques embarqués
- Section Assertions : tableau PASSED/FAILED avec evidence
- Footer : date, version

### PDF
Converti depuis le HTML avec OpenHTMLToPDF.

```java
@Component
public class PdfReportRenderer implements ReportRenderer {
    // Utiliser OpenHTMLToPDF
    // com.openhtmltopdf:openhtmltopdf-pdfbox
}
```

### JSON
Sérialisation JSON de `CampaignReport` complet (Jackson).

---

## 5. Output Directory

```
reports/
  <executionId>/
    campaign.html
    campaign.pdf
    campaign.json
    gatling/
      <simulationId>/      ← copié depuis le répertoire Gatling
        index.html
        ...
```

---

## 6. Publishers

### Interface de Configuration

```java
public record PublisherConfig(
    PublicationTarget target,
    Map<String, String> properties     // paramètres spécifiques à la target
) {}
```

### Confluence
```yaml
reporting:
  publishers:
    - target: CONFLUENCE
      properties:
        url: https://company.atlassian.net
        spaceKey: PERF
        parentPageId: "12345"
        token: ${CONFLUENCE_TOKEN}
```

### S3
```yaml
reporting:
  publishers:
    - target: S3
      properties:
        bucket: perf-reports
        prefix: campaigns/
        region: eu-west-1
```

### Git
```yaml
reporting:
  publishers:
    - target: GIT
      properties:
        repoUrl: https://github.com/company/perf-reports
        branch: main
        path: reports/
        commitMessage: "Add campaign report {executionId}"
        token: ${GIT_TOKEN}
```

### SharePoint
```yaml
reporting:
  publishers:
    - target: SHAREPOINT
      properties:
        siteUrl: https://company.sharepoint.com/sites/perf
        libraryName: "Performance Reports"
        token: ${SHAREPOINT_TOKEN}
```

### Nexus
```yaml
reporting:
  publishers:
    - target: NEXUS
      properties:
        url: https://nexus.company.com
        repository: perf-reports
        groupId: com.company.perf
        token: ${NEXUS_TOKEN}
```

---

## 7. Configuration Multi-Publisher

```yaml
reporting:
  outputDirectory: ./reports
  formats: [HTML, PDF, JSON]
  publishers:
    - target: S3
      properties: { ... }
    - target: CONFLUENCE
      properties: { ... }
```

Tous les publishers configurés reçoivent le rapport.

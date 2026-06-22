# PDR-015 — Reporting Engine

**Module Maven** : `platform-reporting`
**Package** : `com.performance.platform.reporting`
**Statut** : WAITING
**Specs de référence** : `.claude/knowledge/specs/08-report-engine.md` §1-5, §7
**Dépend de** : PDR-001, PDR-002, PDR-004, PDR-013, PDR-014
**Issues** : ISSUE-065, ISSUE-066, ISSUE-067, ISSUE-068, ISSUE-069

---

## Responsabilité

Génère le `CampaignReport` à partir d'un `ExecutionState`, le rend en HTML/PDF/JSON
(OpenHTMLToPDF + Jackson), calcule le `Verdict` final, et écrit les fichiers dans le
répertoire de sortie. La publication vers destinations externes (Confluence/S3/Git) est
dans PDR-016. Inclut l'interface `ReportPublisher` ⚡ (le contrat) et `ReportRenderer`.

---

## Interfaces Publiques

```java
public interface ReportEngine {
    CampaignReport generate(ExecutionState state) throws ReportGenerationException;
}

public interface ReportRenderer {
    byte[] render(CampaignReport report) throws RenderException;
    ReportFormat getFormat();          // HTML | PDF | JSON
}

public interface ReportPublisher {            // ⚡ interface publique critique
    void publish(CampaignReport report, PublisherConfig config) throws PublicationException;
    PublicationTarget getTarget();
}
```

### Records du rapport

```java
public record CampaignReport(
    ReportId id, ScenarioId scenarioId, String scenarioName, String scenarioVersion,
    List<String> tags, Map<String, String> metadata,
    EnvironmentInfo environment, ExecutionSummary executionSummary,
    List<TaskReportEntry> preparationResults,
    List<InjectionReportEntry> injectionResults,
    List<AssertionReportEntry> assertionResults,
    Verdict verdict, String verdictReason,
    Instant generatedAt, Duration totalDuration
) {}

public record EnvironmentInfo(List<String> agentIds, String jvmVersion, Map<String, String> systemProperties) {}
public record ExecutionSummary(int totalTasks, int successfulTasks, int failedTasks, int skippedTasks,
    Duration preparationDuration, Duration injectionDuration, Duration assertionDuration) {}
public record TaskReportEntry(TaskId taskId, String taskName, TaskStatus status, Duration duration, Map<String, Object> outputs) {}
public record InjectionReportEntry(TaskId taskId, InjectionResult metrics, Path gatlingReportDirectory) {}
public record AssertionReportEntry(TaskId assertionId, AssertionResult result, Evidence evidence) {}

public record PublisherConfig(PublicationTarget target, Map<String, String> properties) {}

public class RenderException extends RuntimeException {
    public RenderException(String message, Throwable cause) { super(message, cause); }
}
public class PublicationException extends RuntimeException {
    public PublicationException(String message, Throwable cause) { super(message, cause); }
}
```

### Renderers / Engine

```java
@Component public class HtmlReportRenderer implements ReportRenderer { /* Thymeleaf/OpenHTMLToPDF */ }
@Component public class PdfReportRenderer implements ReportRenderer { /* OpenHTMLToPDF pdfbox */ }
@Component public class JsonReportRenderer implements ReportRenderer { /* Jackson */ }
@Service   public class DefaultReportEngine implements ReportEngine { /* écoute ScenarioFinished */ }
```

---

## Règles de Comportement

- `TaskReportEntry` utilise `String taskName` (pas `TaskType`).
- Verdict : SUCCESS (toutes PASSED), WARNING (≥1 FAILED severity WARNING), FAILED (≥1 FAILED severity ERROR).
- PDF généré depuis le HTML (OpenHTMLToPDF).
- JSON = sérialisation complète de `CampaignReport`.
- Sortie : `reports/<executionId>/campaign.{html,pdf,json}` + copie du répertoire Gatling.
- `ReportPublisher` ⚡ : modification = ADR.
- `DefaultReportEngine` écoute `ScenarioFinished` et déclenche la génération, publie `ReportGenerated`.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → ReportId, ScenarioId, TaskId, Verdict, InjectionResult, AssertionResult,
            Evidence, TaskStatus, ReportFormat, PublicationTarget, ExecutionState
  PDR-002 → ScenarioFinished, ReportGenerated
  PDR-004 → GenerateReportUseCase, ExecutionRepository
  PDR-013 → InjectionResult
  PDR-014 → AssertionResult

Ce PDR est utilisé par :
  PDR-016 (publishers)   → implémentent ReportPublisher
  PDR-018 (platform-app) → orchestration finale
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Test : génération HTML/PDF/JSON + calcul verdict (SUCCESS/WARNING/FAILED)
- [ ] `ReportPublisher` ⚡, `ReportEngine` dans `.claude/context/interfaces-registry.md` STABLE

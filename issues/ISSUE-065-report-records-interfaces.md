# ISSUE-065 — Records CampaignReport + interfaces ReportEngine/Renderer/Publisher

**PDR** : PDR-015
**Module** : `platform-reporting`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-006, ISSUE-013, ISSUE-002
**Estime** : M

---

## Objectif

Créer le module `platform-reporting`, les records du rapport et les interfaces publiques
(`ReportEngine`, `ReportRenderer`, `ReportPublisher` ⚡).

## Fichiers à Créer

```
platform-reporting/pom.xml — dépend de domain, application, openhtmltopdf, jackson
platform-reporting/src/main/java/com/performance/platform/reporting/
  ├── ReportEngine.java
  ├── ReportRenderer.java
  ├── ReportPublisher.java        — ⚡ interface critique
  ├── model/CampaignReport.java
  ├── model/EnvironmentInfo.java
  ├── model/ExecutionSummary.java
  ├── model/TaskReportEntry.java
  ├── model/InjectionReportEntry.java
  ├── model/AssertionReportEntry.java
  ├── model/PublisherConfig.java
  ├── RenderException.java
  └── PublicationException.java

platform-reporting/src/test/java/com/performance/platform/reporting/model/
  └── CampaignReportTest.java
```

## Interfaces à Implémenter

```java
public interface ReportEngine { CampaignReport generate(ExecutionState state) throws ReportGenerationException; }
public interface ReportRenderer { byte[] render(CampaignReport report) throws RenderException; ReportFormat getFormat(); }
public interface ReportPublisher { void publish(CampaignReport report, PublisherConfig config) throws PublicationException; PublicationTarget getTarget(); }

public record CampaignReport(ReportId id, ScenarioId scenarioId, String scenarioName, String scenarioVersion,
    List<String> tags, Map<String, String> metadata, EnvironmentInfo environment, ExecutionSummary executionSummary,
    List<TaskReportEntry> preparationResults, List<InjectionReportEntry> injectionResults,
    List<AssertionReportEntry> assertionResults, Verdict verdict, String verdictReason,
    Instant generatedAt, Duration totalDuration) {}
public record EnvironmentInfo(List<String> agentIds, String jvmVersion, Map<String, String> systemProperties) {}
public record ExecutionSummary(int totalTasks, int successfulTasks, int failedTasks, int skippedTasks,
    Duration preparationDuration, Duration injectionDuration, Duration assertionDuration) {}
public record TaskReportEntry(TaskId taskId, String taskName, TaskStatus status, Duration duration, Map<String, Object> outputs) {}
public record InjectionReportEntry(TaskId taskId, InjectionResult metrics, Path gatlingReportDirectory) {}
public record AssertionReportEntry(TaskId assertionId, AssertionResult result, Evidence evidence) {}
public record PublisherConfig(PublicationTarget target, Map<String, String> properties) {}
```

## Règles Spécifiques

- `TaskReportEntry` utilise `String taskName` (pas `TaskType`).
- `ReportPublisher` ⚡ : modification = ADR.

## Critères de Done

- [ ] `mvn test -pl platform-reporting -q` → 0 erreur
- [ ] Records et interfaces compilent
- [ ] `progress.md` mis à jour : ISSUE-065 → DONE
- [ ] `context/interfaces-registry.md` : `ReportEngine`, `ReportRenderer`, `ReportPublisher` ⚡, `CampaignReport` → STABLE

# ISSUE-069 — ReportFileWriter (output directory + copie Gatling)

**PDR** : PDR-015
**Module** : `platform-reporting`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-067, ISSUE-068
**Estime** : M

---

## Objectif

Implémenter l'écriture des rapports sur disque dans la structure
`reports/<executionId>/campaign.{html,pdf,json}` + copie du répertoire Gatling.

## Fichiers à Créer

```
platform-reporting/src/main/java/com/performance/platform/reporting/output/
  ├── ReportFileWriter.java
  └── ReportProperties.java   — outputDirectory, formats

platform-reporting/src/test/java/com/performance/platform/reporting/output/
  └── ReportFileWriterTest.java — écriture sur @TempDir
```

## Interfaces à Implémenter

```java
@Component
public class ReportFileWriter {
    public ReportFileWriter(List<ReportRenderer> renderers, ReportProperties props) { /* ... */ }
    public Path write(ExecutionId executionId, CampaignReport report);
}

@ConfigurationProperties(prefix = "reporting")
public record ReportProperties(String outputDirectory, List<ReportFormat> formats) {}
```

## Règles Spécifiques

- Structure : `reports/<executionId>/campaign.html|pdf|json`.
- Copier le répertoire Gatling sous `reports/<executionId>/gatling/<simId>/`.
- N'écrire que les formats configurés (`reporting.formats`).
- I/O sous Virtual Threads.

## Critères de Done

- [ ] `mvn test -pl platform-reporting -q` → 0 erreur
- [ ] Les 3 fichiers écrits dans `reports/<id>/` selon les formats configurés
- [ ] `progress.md` mis à jour : ISSUE-069 → DONE
- [ ] `context/interfaces-registry.md` mis à jour

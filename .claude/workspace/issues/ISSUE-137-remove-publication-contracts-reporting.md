# ISSUE-137 — Supprimer les contrats de publication de platform-reporting

**PDR** : PDR-031
**Module** : `platform-reporting`
**Statut** : WAITING
**Priorité** : P1 (critique)
**Bloquée par** : ISSUE-135
**Estime** : S (< 1h)

---

## Objectif

Supprimer les types de publication du module `platform-reporting` : `ReportPublisher`,
`PublicationTarget`, `PublicationException`, et le record `PublisherConfig`. Ces types ne sont
plus référencés (consommateurs supprimés en ISSUE-135). Nettoyer `CampaignReportTest`.
`CampaignReport`, `ReportEngine`, `ReportRenderer`, `ReportFileWriter` restent **inchangés**.

## Fichiers à Créer / Modifier

```
SUPPRIMER :
platform-reporting/src/main/java/com/performance/platform/reporting/ReportPublisher.java
platform-reporting/src/main/java/com/performance/platform/reporting/PublicationTarget.java
platform-reporting/src/main/java/com/performance/platform/reporting/PublicationException.java
platform-reporting/src/main/java/com/performance/platform/reporting/model/PublisherConfig.java   (si présent)

MODIFIER :
platform-reporting/src/test/java/.../CampaignReportTest.java
  — retirer toute donnée/import utilisant PublicationTarget / PublisherConfig
```

## Interfaces à Implémenter

> Suppressions actées par ADR-022 :

```java
// SUPPRIMER
public interface ReportPublisher { void publish(...) throws PublicationException; PublicationTarget getTarget(); }
public enum PublicationTarget { CONFLUENCE, S3, SHAREPOINT, GIT, NEXUS }
public class PublicationException extends Exception { ... }
public record PublisherConfig(PublicationTarget target, Map<String,String> properties) { }
```

```java
// CONSERVER (ne pas toucher)
public record CampaignReport(...) { }      // ne référence aucun type de publication
public interface ReportEngine { ... }
public interface ReportRenderer { ... }
// ReportFileWriter, ReportProperties (reporting.output-directory, reporting.formats)
```

## Règles Spécifiques

- Vérifier d'abord `grep -rn "PublicationTarget\|ReportPublisher\|PublisherConfig\|PublicationException"
  platform-reporting platform-infrastructure platform-application` → seules restent les
  définitions + `CampaignReportTest`. Sinon, escalader (ordre PDR-031 non respecté).
- `CampaignReport` ne contient aucun champ de type publication : ne pas modifier le record.
- Vérifier que `ReportFormat` (HTML/PDF/JSON) et les renderers ne sont pas impactés.

## Critères de Done

- [ ] `mvn test -pl platform-reporting -q` → 0 erreur
- [ ] `ReportPublisher`, `PublicationTarget`, `PublicationException`, `PublisherConfig` n'existent plus
- [ ] `grep -rn "PublicationTarget" platform-reporting/src` → vide
- [ ] `CampaignReport` / renderers / `ReportFileWriter` inchangés et verts
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` : contrats publication reporting → `❌ REMOVED (ADR-022)`
</content>

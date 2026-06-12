# ISSUE-071 — ConfluenceReportPublisher

**PDR** : PDR-016
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-070
**Estime** : M

---

## Objectif

Implémenter `ConfluenceReportPublisher` : publie le rapport HTML comme page Confluence.

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/publisher/confluence/
  └── ConfluenceReportPublisher.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/publisher/confluence/
  └── ConfluenceReportPublisherTest.java — mock HTTP server
```

## Interfaces à Implémenter

```java
@Component
@ConditionalOnProperty(name = "reporting.publishers", value = "CONFLUENCE")
public class ConfluenceReportPublisher implements ReportPublisher {
    public PublicationTarget getTarget() { return PublicationTarget.CONFLUENCE; }
    public void publish(CampaignReport report, PublisherConfig config) throws PublicationException { /* ... */ }
}
```

## Règles Spécifiques

- Config (`PublisherConfig.properties`) : `url`, `spaceKey`, `parentPageId`, `token`.
- Token jamais en clair (lu depuis env/Secret).
- HTTP client sous Virtual Threads. Échec → `PublicationException`.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Création de page testée contre un serveur HTTP mock
- [ ] `progress.md` mis à jour : ISSUE-071 → DONE
- [ ] `context/interfaces-registry.md` : `ConfluenceReportPublisher` → STABLE

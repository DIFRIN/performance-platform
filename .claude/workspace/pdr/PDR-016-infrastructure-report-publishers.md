# PDR-016 — Report Publishers (Infrastructure)

**Module Maven** : `platform-infrastructure`
**Package** : `com.performance.platform.infrastructure.publisher`
**Statut** : WAITING
**Specs de référence** : `.claude/knowledge/specs/08-report-engine.md` §6-7, `.claude/knowledge/specs/03-task-framework.md` §7.6
**Dépend de** : PDR-001, PDR-004, PDR-015
**Issues** : ISSUE-070, ISSUE-071, ISSUE-072, ISSUE-073

---

## Responsabilité

Implémente les `ReportPublisher` concrets vers des destinations externes : Confluence, S3,
Git (+ SharePoint, Nexus en option). Sélection par `@ConditionalOnProperty` sur la liste
`reporting.publishers[].target`. Supporte un publisher custom par nom de classe qualifié.

**Séparation stricte** : tout dans `com.performance.platform.infrastructure.publisher/`.
Ne déborde PAS sur `.executor`, `.plugin`, `.persistence`.

---

## Interfaces Publiques

```java
@Component
@ConditionalOnProperty(name = "reporting.publishers", value = "CONFLUENCE")
public class ConfluenceReportPublisher implements ReportPublisher {
    public PublicationTarget getTarget() { return PublicationTarget.CONFLUENCE; }
    public void publish(CampaignReport report, PublisherConfig config) throws PublicationException { /* ... */ }
}

@Component
public class S3ReportPublisher implements ReportPublisher {
    public PublicationTarget getTarget() { return PublicationTarget.S3; }
    public void publish(CampaignReport report, PublisherConfig config) throws PublicationException { /* ... */ }
}

@Component
public class GitReportPublisher implements ReportPublisher {
    public PublicationTarget getTarget() { return PublicationTarget.GIT; }
    public void publish(CampaignReport report, PublisherConfig config) throws PublicationException { /* ... */ }
}

// Dispatcher : route un report vers tous les publishers configurés
@Component
public class MultiPublisherDispatcher implements ReportPublisherPort {
    public MultiPublisherDispatcher(List<ReportPublisher> publishers, ReportEngine engine) { /* ... */ }
    public void publish(ReportId reportId, ExecutionId executionId) { /* ... */ }
}
```

---

## Règles de Comportement

- Sélection par configuration uniquement — pas de `if (target == S3)` métier.
- Tous les publishers configurés reçoivent le rapport (multi-publisher).
- Custom publisher via `reporting.publishers[].custom-class` (nom qualifié implémentant `ReportPublisher`).
- Secrets (tokens) jamais en clair : lus depuis env/Secret K8s (CNF-03).
- Échec d'un publisher → loggé, n'empêche pas les autres ; publie `ReportPublished` par succès.
- I/O réseau sous Virtual Threads.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → PublicationTarget, ReportId, ExecutionId
  PDR-004 → ReportPublisherPort
  PDR-015 → ReportPublisher, CampaignReport, PublisherConfig, PublicationException, ReportEngine

Ce PDR est utilisé par :
  PDR-018 (platform-app) → assemblage des publishers selon config
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Test : dispatcher envoie à tous les publishers configurés
- [ ] ArchUnit : code dans `.publisher` uniquement
- [ ] Publishers dans `.claude/context/interfaces-registry.md` STABLE

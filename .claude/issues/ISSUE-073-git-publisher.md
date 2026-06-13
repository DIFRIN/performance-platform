# ISSUE-073 — GitReportPublisher

**PDR** : PDR-016
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-070
**Estime** : M

---

## Objectif

Implémenter `GitReportPublisher` : commit + push des fichiers du rapport dans un dépôt Git.

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/publisher/git/
  └── GitReportPublisher.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/publisher/git/
  └── GitReportPublisherTest.java — dépôt Git local de test (JGit)
```

## Interfaces à Implémenter

```java
@Component
@ConditionalOnProperty(name = "reporting.publishers", value = "GIT")
public class GitReportPublisher implements ReportPublisher {
    public PublicationTarget getTarget() { return PublicationTarget.GIT; }
    public void publish(CampaignReport report, PublisherConfig config) throws PublicationException { /* ... */ }
}
```

## Règles Spécifiques

- Config : `repoUrl`, `branch`, `path`, `commitMessage` (template `{executionId}`), `token`.
- Token jamais en clair.
- Utiliser JGit ; commit + push sous Virtual Threads.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Commit créé dans un dépôt Git local de test
- [ ] `.claude/progress.md` mis à jour : ISSUE-073 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `GitReportPublisher` → STABLE

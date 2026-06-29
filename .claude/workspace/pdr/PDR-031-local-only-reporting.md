# PDR-031 — Local-Only Reporting (suppression des publishers)

**Module Maven** : `platform-infrastructure`, `platform-application`, `platform-reporting`, `platform-app`
**Package** : `com.performance.platform.infrastructure.publisher` (supprimé),
`com.performance.platform.application.ports.out`, `com.performance.platform.reporting`
**Statut** : WAITING
**Specs de référence** : ADR-022 (Report output local only), spec 08-report-engine.md §6/§7
(publishers — désormais SUPERSEDED par ADR-022)
**Dépend de** : rien (peut démarrer indépendamment de PDR-030, mais valider le build après)
**Issues** : ISSUE-135, ISSUE-136, ISSUE-137, ISSUE-138

---

## Responsabilité

Retirer entièrement le système de **publication de rapports vers des systèmes externes**
(Confluence, S3, Git) et le port associé. La plateforme génère uniquement des **fichiers de
rapport locaux** (HTML / PDF / JSON) via `ReportFileWriter` (ISSUE-069, inchangé). La diffusion
externe devient la responsabilité du CI/CD utilisateur opérant sur les fichiers générés.

Ce qu'il ne fait PAS : il **ne touche pas** à `ReportEngine`, `ReportFileWriter`,
`ReportRenderer` (HTML/PDF/JSON), ni au record `CampaignReport` lui-même (qui ne référence pas
les types de publication). `ReportProperties` (`reporting.output-directory`, `reporting.formats`)
est conservé.

---

## Interfaces Publiques

> Ce PDR **supprime** des interfaces publiques. Suppressions actées par ADR-022
> (« modification d'interface publique = ADR »).

**Supprimés — `platform-reporting`** :
```java
// SUPPRIMER
public interface ReportPublisher { void publish(...); PublicationTarget getTarget(); }
public enum PublicationTarget { CONFLUENCE, S3, SHAREPOINT, GIT, NEXUS }
public class PublicationException extends Exception { ... }
public record PublisherConfig(PublicationTarget target, Map<String,String> properties) { }
```

**Supprimé — `platform-application`** :
```java
// SUPPRIMER (aucun appelant — uniquement implémenté par MultiPublisherDispatcher)
public interface ReportPublisherPort { ... }
```

**Supprimés — `platform-infrastructure` (package `.publisher`)** :
```
MultiPublisherDispatcher, PublishersProperties,
confluence/ConfluenceReportPublisher, s3/S3ReportPublisher, git/GitReportPublisher
(+ leurs tests)
```

**Conservés (inchangés)** :
```java
public interface ReportEngine { CampaignReport generate(ExecutionState state) ...; }
public interface ReportRenderer { byte[] render(CampaignReport report) ...; ReportFormat getFormat(); }
public record CampaignReport(...) { }     // ne référence aucun type de publication
// ReportFileWriter + ReportProperties (reporting.output-directory, reporting.formats)
```

---

## Règles de Comportement

- **Ordre de suppression** : supprimer d'abord les **consommateurs** (adapters infra
  `.publisher`) avant les **contrats** (`platform-reporting` / `platform-application`), sinon le
  build casse (références non résolues). D'où ISSUE-135 (infra) → ISSUE-136/137 (contrats).
- **Pas de no-op, pas de port conservé « au cas où »** : `ReportPublisherPort` n'a aucun
  appelant réel → suppression franche (YAGNI, ADR-022). Réintroductible proprement au besoin.
- **Tests à nettoyer** :
  - `CampaignReportTest` (platform-reporting) : retirer toute donnée de test utilisant
    `PublicationTarget`/`PublisherConfig`.
  - `PortsCompileTest` (platform-application) : retirer la référence à `ReportPublisherPort`.
  - `InfrastructurePackageSeparationTest` (ArchUnit) : les règles `publisher` utilisent
    `allowEmptyShould` → deviennent vacuellement vraies, **aucune modification requise**
    (vérifier qu'elles compilent toujours sans le package).
- **Config & doc** : retirer les blocs `platform.publishers` (commentés ou non) de
  `platform-app/src/main/resources/application.yaml` et `application-orchestrator.yaml` ;
  retirer la section publishers du README / doc déploiement et ajouter la **recommandation
  CI/CD** (ex. `aws s3 cp ./reports ...`).
- **Dépendances Maven** : si `platform-infrastructure/pom.xml` portait des dépendances
  uniquement pour les publishers supprimés, les retirer — **attention** : `WireMock` est aussi
  utilisé par `MockServerTaskExecutor` (main) ; ne pas le retirer. Vérifier `mvn dependency:tree`.

---

## Dépendances Techniques

```
Ce PDR utilise :
  platform-reporting (ReportFileWriter, CampaignReport — conservés)

Ce PDR est utilisé par :
  rien (suppression de fonctionnalité)
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE (ISSUE-135..138)
- [ ] Le package `platform-infrastructure/.../publisher/` n'existe plus (main + test)
- [ ] `ReportPublisherPort`, `ReportPublisher`, `PublicationTarget`, `PublicationException`,
      `PublisherConfig` n'existent plus ; aucune référence résiduelle (`grep` vide)
- [ ] Aucun bloc `platform.publishers` dans les yamls de `platform-app`
- [ ] `mvn clean install` vert ; ArchUnit infra vert
- [ ] `.claude/workspace/interfaces-registry.md` : entrées publishers marquées `❌ REMOVED (ADR-022)`
</content>

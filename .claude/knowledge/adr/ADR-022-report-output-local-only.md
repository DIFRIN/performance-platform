# ADR-022 — Sortie des rapports en fichiers locaux uniquement (publication externe via CI/CD)

**Date** : 2026-06-29
**Statut** : ACCEPTED
**Décideurs** : Architect (décision utilisateur)
**Supersede** : PDR-016 (Report Publishers) et ses Issues ISSUE-070..073 — fonctionnalité retirée.

---

## Contexte

La plateforme a été dotée (PDR-016) d'un système de publication de rapports vers des
systèmes externes :

- `ConfluenceReportPublisher` (ISSUE-071) — push HTML vers une page Confluence ;
- `S3ReportPublisher` (ISSUE-072) — upload vers un bucket S3 (signature AWS maison) ;
- `GitReportPublisher` (ISSUE-073) — commit/push dans un dépôt Git ;
- `MultiPublisherDispatcher` (ISSUE-070) — orchestrateur qui implémente le port
  `ReportPublisherPort` (platform-application) et dispatche vers chaque
  `ReportPublisher` (platform-reporting) selon `PublishersProperties`
  (`platform.publishers.*`).

Cette mécanique représente une **sur-ingénierie** :

1. Elle embarque dans le fat JAR des clients spécifiques (signature AWS S3 manuscrite,
   API REST Confluence, invocation de `git` en sous-processus) qu'il faut maintenir,
   sécuriser et tester (WireMock x3) alors qu'ils ne font pas partie du cœur métier
   « tester la performance ».
2. Les credentials externes (token Confluence, clés AWS, identité Git) doivent transiter
   par la JVM de la plateforme — surface d'attaque et complexité de configuration accrues.
3. Le besoin réel est : **produire des artefacts de rapport (JSON / PDF / HTML)**. Leur
   diffusion (Confluence, S3, Git, Slack, mail, …) est une préoccupation d'orchestration
   CI/CD, mieux servie par des outils dédiés (`aws s3 cp`, `git`, l'API Confluence d'un
   step de pipeline) opérant sur les fichiers déjà générés.

Le `ReportFileWriter` (ISSUE-069, `platform-reporting`) écrit déjà les rapports sur disque
dans `reporting.output-directory`. C'est la seule sortie nécessaire.

---

## Décision

**La plateforme génère uniquement des fichiers de rapport locaux (JSON / PDF / HTML) via
`ReportFileWriter`. Toute publication vers un système externe (Confluence, S3, Git, …) est
hors-périmètre de la plateforme et se fait par des scripts/steps CI/CD externes opérant sur
les fichiers générés.**

Concrètement :

1. **Suppression des adapters de publication** (`platform-infrastructure`, package
   `.publisher`) :
   - `ConfluenceReportPublisher`, `S3ReportPublisher`, `GitReportPublisher` + leurs tests ;
   - `MultiPublisherDispatcher` + son test ;
   - `PublishersProperties` + son test.

2. **Suppression du port sortant** `ReportPublisherPort` (`platform-application`). Le port
   n'a aucun appelant dans l'engine ni dans aucun use case : il n'était implémenté que par
   `MultiPublisherDispatcher` et n'est invoqué nulle part. Il n'y a donc **aucun point
   d'extension à préserver** ; on ne garde pas de no-op. Si un besoin de publication
   in-process réapparaît, un nouveau port pourra être (re)défini à ce moment-là avec un
   appelant réel (YAGNI).

3. **Suppression des contrats de publication du module `platform-reporting`** :
   `ReportPublisher`, `PublicationTarget`, `PublisherConfig`, `PublicationException`.
   Ces types ne sont référencés que par le système de publishers supprimé (et par
   `CampaignReportTest` qui les utilise comme données de test à nettoyer). Le record
   `CampaignReport` lui-même ne les utilise pas.

4. **Nettoyage de la configuration et de la documentation** : retrait des blocs
   `platform.publishers` (commentés) dans `application.yaml` / `application-orchestrator.yaml`,
   et de toute référence aux publishers dans le README / la doc de déploiement.

5. **`ReportFileWriter` reste la seule sortie.** `reporting.output-directory` et
   `reporting.formats` (HTML / PDF / JSON) sont conservés tels quels.

### Recommandation d'usage CI/CD (documentaire, hors code plateforme)

```bash
# Exemple : la plateforme génère les rapports dans ./reports, le pipeline les diffuse.
java -jar performance-platform.jar --scenario=scenario.yaml
aws s3 cp ./reports/ s3://my-bucket/reports/ --recursive   # diffusion = responsabilité CI/CD
```

---

## Justification

- **Cœur métier focalisé** : la plateforme injecte de la charge, exécute des assertions et
  produit des rapports. La diffusion est une préoccupation transverse d'intégration, pas du
  cœur.
- **Sécurité (CNF-03)** : plus aucun credential externe (AWS, Confluence, Git) ne transite
  par la JVM plateforme. Les secrets restent dans le coffre du pipeline CI/CD.
- **Moins de dépendances et de surface de test** : suppression des clients HTTP/sous-process
  spécifiques et des tests WireMock associés ; fat JAR allégé.
- **YAGNI / extensibilité différée** : aucun appelant réel de `ReportPublisherPort`
  n'existe ; conserver un port + no-op « au cas où » est de la spéculation. Le port pourra
  être réintroduit proprement le jour où un besoin in-process concret apparaît.

---

## Conséquences

**Positives** :
- Surface de code, de dépendances et de tests réduite ; build plus rapide.
- Configuration simplifiée (plus de `platform.publishers`).
- Aucun secret externe dans la plateforme.

**Négatives / Contraintes** :
- La diffusion automatique des rapports devient la responsabilité du pipeline CI/CD
  (à documenter dans le README).
- Suppression d'API publiques (`ReportPublisherPort`, `ReportPublisher`,
  `PublicationTarget`, `PublisherConfig`, `PublicationException`) → conforme à la règle
  « modification d'interface publique = ADR » (cet ADR).

**Fichiers impactés** :
- `platform-infrastructure/.../publisher/**` — supprimé (5 classes main + 5 tests).
- `platform-application/.../ports/out/ReportPublisherPort.java` — supprimé ;
  `PortsCompileTest` nettoyé.
- `platform-reporting/.../ReportPublisher.java`, `PublicationTarget.java`,
  `PublicationException.java`, `model/PublisherConfig.java` — supprimés ;
  `CampaignReportTest` nettoyé.
- `platform-app/src/main/resources/application.yaml` + `application-orchestrator.yaml` —
  blocs `platform.publishers` retirés.
- README / doc déploiement — section publishers retirée, recommandation CI/CD ajoutée.
- `InfrastructurePackageSeparationTest` (ArchUnit) : les règles `publisher` utilisent déjà
  `allowEmptyShould` → deviennent vacuellement vraies, **aucune modification requise**.

---

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Conserver `ReportPublisherPort` en no-op « pour extensibilité » | Aucun appelant existant ; spéculation (YAGNI). Réintroductible proprement au besoin. |
| Conserver uniquement S3 (le plus « standard ») | Même problème de credentials et de maintenance ; `aws s3 cp` côté CI le fait mieux. |
| Garder les publishers mais désactivés par défaut | Le code mort reste à maintenir et tester ; ne réduit pas la surface. |

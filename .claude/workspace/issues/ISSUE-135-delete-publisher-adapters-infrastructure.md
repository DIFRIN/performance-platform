# ISSUE-135 — Supprimer les adapters publisher de platform-infrastructure

**PDR** : PDR-031
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1 (critique)
**Bloquée par** : —
**Estime** : M (1-3h)

---

## Objectif

Supprimer entièrement le package `com.performance.platform.infrastructure.publisher` (adapters
de publication Confluence / S3 / Git + dispatcher + properties) et leurs tests. C'est la
**première** étape de PDR-031 : les consommateurs des contrats de publication doivent partir
avant les contrats eux-mêmes (ISSUE-136/137).

## Fichiers à Créer / Modifier

```
SUPPRIMER (main) :
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/publisher/
  ├── MultiPublisherDispatcher.java
  ├── PublishersProperties.java
  ├── confluence/ConfluenceReportPublisher.java
  ├── s3/S3ReportPublisher.java
  └── git/GitReportPublisher.java

SUPPRIMER (test) :
platform-infrastructure/src/test/java/com/performance/platform/infrastructure/publisher/
  ├── MultiPublisherDispatcherTest.java
  ├── confluence/ConfluenceReportPublisherTest.java
  ├── s3/S3ReportPublisherTest.java
  └── git/GitReportPublisherTest.java

MODIFIER :
platform-infrastructure/pom.xml  — retirer les dépendances utilisées UNIQUEMENT par les
                                   publishers supprimés (le cas échéant). NE PAS retirer
                                   WireMock (utilisé aussi par MockServerTaskExecutor main).
```

## Interfaces à Implémenter

> Aucune nouvelle interface. Suppression d'adapters (ADR-022). Après suppression, le port
> `ReportPublisherPort` (platform-application) n'a plus aucun implémenteur — il sera supprimé
> en ISSUE-136.

## Règles Spécifiques

- Supprimer le **répertoire `publisher/` complet** (main + test), y compris les sous-packages
  `confluence/`, `s3/`, `git/`.
- `InfrastructurePackageSeparationTest` (ArchUnit) : les règles sur le package `publisher`
  utilisent `allowEmptyShould` → deviennent vacuellement vraies. **Vérifier** qu'elles
  compilent et passent toujours sans le package ; ne pas les supprimer sauf si elles
  référencent une classe désormais absente (auquel cas, retirer la règle morte).
- `pom.xml` : avant de retirer une dépendance, vérifier `grep` qu'elle n'est plus utilisée
  ailleurs dans `src/main`. **WireMock reste** (MockServerTaskExecutor). Vérifier
  `mvn dependency:tree` après.
- À l'issue de cette Issue, le module **ne compile pas encore** tant que `ReportPublisherPort`
  est référencé par rien d'autre — vérifier : si `MultiPublisherDispatcher` était le seul
  implémenteur, sa suppression suffit à ce que le module compile (le port reste défini dans
  platform-application, sans implémenteur, ce qui est légal).

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur (le module compile sans le package publisher)
- [ ] Le répertoire `.../infrastructure/publisher/` n'existe plus (main + test)
- [ ] `grep -r "ReportPublisher\b" platform-infrastructure/src` → vide
- [ ] ArchUnit infra vert
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` : entrées publishers infra → `❌ REMOVED (ADR-022)`
</content>

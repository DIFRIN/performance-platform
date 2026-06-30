# ISSUE-134 — Retirer les versions Spring explicites des poms enfants restants

**PDR** : PDR-030
**Module** : poms enfants (build config cross-cutting — voir note périmètre)
**Statut** : DONE
**Priorité** : P0 (bloquant — finalise la centralisation BOM)
**Bloquée par** : ISSUE-132
**Estime** : M (1-3h)

---

## Objectif

Retirer toutes les `<version>` Spring / Spring Boot / Spring Data / Spring Kafka /
Spring Modulith des poms enfants restants, pour qu'elles soient héritées du BOM (ISSUE-132).
Conserver les versions des artefacts **non gérés par le BOM**. Résultat : une seule source de
vérité (`spring-boot.version` au root).

> **Note de périmètre** : cette Issue est volontairement **cross-cutting sur plusieurs poms**
> (exception assumée à la règle « un module par Issue ») car (a) elle ne touche **aucun code
> `src/`**, uniquement des `pom.xml`, et (b) le retrait de versions ne se valide que par un
> **build reactor complet** (les inter-dépendances rendent un nettoyage module-par-module non
> isolable). `platform-execution-engine` est traité séparément (ISSUE-133, conflit critique).

## Fichiers à Créer / Modifier

```
platform-infrastructure/pom.xml   — retirer versions : spring-context, spring-web, spring-jdbc,
                                     spring-boot, spring-boot-autoconfigure, spring-data-jpa,
                                     spring-kafka (gérés par le BOM)
platform-observability/pom.xml     — retirer version : spring-boot
platform-app/pom.xml               — retirer versions des spring-boot-starter-* (web, core,
                                     security, oauth2-resource-server, actuator, test) ;
                                     CONSERVER ${spring-boot.version} sur le spring-boot-maven-plugin
platform-transport/pom.xml         — retirer versions Spring/Spring Kafka si présentes
platform-agent-runtime/pom.xml     — retirer versions Spring si présentes
platform-scenario-dsl/pom.xml      — retirer versions Spring si présentes
platform-injection-gatling/pom.xml — retirer versions Spring si présentes
platform-assertion/pom.xml         — retirer versions Spring si présentes
platform-reporting/pom.xml         — retirer versions Spring si présentes
platform-application/pom.xml       — retirer versions Spring si présentes
```

## Interfaces à Implémenter

> Règle mécanique (exemple) :

```xml
<!-- AVANT -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
    <version>7.0.0</version>      <!-- géré par le BOM → RETIRER -->
</dependency>
<!-- APRÈS -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
</dependency>
```

## Règles Spécifiques

- **Retirer** la `<version>` uniquement si l'artefact est **géré par le BOM**
  (`spring-boot-dependencies` couvre : spring-*, spring-boot-*, spring-boot-starter-*,
  spring-data-*, spring-kafka, spring-tx, spring-orm, et la plupart des libs alignées Spring Boot
  dont jackson, hibernate, micrometer, postgresql, h2, flyway, testcontainers, assertj, mockito,
  awaitility, logback…). **Vérifier au cas par cas** : si `mvn dependency:tree` montre que le
  BOM fournit déjà la version, retirer ; sinon conserver.
- **Conserver explicitement** la version des artefacts hors BOM réellement non gérés
  (ex. ArchUnit, WireMock standalone, logstash-logback-encoder, OpenHTMLToPDF, kafka-clients si
  version spécifique, jakarta.persistence-api 3.2 override volontaire). En cas de doute :
  retirer, builder, et si la résolution change ou casse → remettre la version + commentaire.
- **`platform-app`** : garder `${spring-boot.version}` **uniquement** sur le
  `spring-boot-maven-plugin` (plugin non géré par `<dependencyManagement>`). La property locale
  peut être supprimée au profit de celle du root si héritée.
- **Ne pas toucher** `platform-domain` ni `platform-plugin-api` (aucune dépendance Spring).
- Procéder module par module mais **valider par un `mvn clean install` global** à la fin.

## Critères de Done

- [ ] `mvn clean install -q` → 0 erreur (reactor complet)
- [ ] `mvn dependency:tree` : aucune `<version>` Spring/Boot/Data/Kafka/Modulith explicite dans
      un pom enfant (toutes héritées du BOM) — vérifier par inspection des poms
- [ ] Aucune régression de version : Spring Boot reste 4.0.0, Spring 7.x cohérent partout
- [ ] `mvn test -q` (sans Docker) reste vert (modulo IT skipped, hors périmètre de cette Issue)
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (note BOM ADR-024)
</content>

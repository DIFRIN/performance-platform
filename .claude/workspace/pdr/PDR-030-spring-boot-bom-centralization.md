# PDR-030 — Spring Boot BOM Centralization

**Module Maven** : root `pom.xml` + tous les modules enfants
**Package** : — (configuration de build uniquement, aucun code Java)
**Statut** : WAITING
**Specs de référence** : ADR-024 (Spring Boot 4 BOM single source), CLAUDE.md §4 (stack),
constraints.md CC-03 (dépendances), CD-03 (versions)
**Dépend de** : rien (chantier fondation, à exécuter en premier)
**Issues** : ISSUE-132, ISSUE-133, ISSUE-134

---

## Responsabilité

Centraliser toutes les versions Spring / Spring Boot / Spring Data / Spring Kafka /
Spring Modulith dans le root `pom.xml` via l'**import du BOM `spring-boot-dependencies`**
(et `spring-modulith-bom`), et **supprimer toutes les versions Spring explicites** des poms
enfants. Résout le conflit latent **Spring 6.2.6 vs Spring Boot 4 (Spring 7)** dans
`platform-execution-engine`.

Ce PDR ne touche **aucun code Java** : uniquement des `pom.xml`. Il ne change pas les versions
fonctionnelles (Spring Boot reste 4.0.0) — il en fait une **source unique de vérité**.

Ce qu'il ne fait PAS : il n'introduit pas `spring-boot-starter-parent` comme parent (le root
conserve son `pluginManagement` custom). Il ne retire pas les versions des artefacts **non
gérés par le BOM** (Flyway, HikariCP si non aligné, ArchUnit, WireMock, Mockito, AssertJ,
logstash-logback-encoder, micrometer si version spécifique, kafka-clients, jakarta.persistence
override, postgresql, testcontainers, h2, OpenHTMLToPDF…).

---

## Interfaces Publiques

> Aucune interface Java. La « surface » de ce PDR est la configuration Maven.

Structure cible du root `pom.xml` (`<dependencyManagement>`) :

```xml
<properties>
    <java.version>25</java.version>
    <maven.compiler.source>25</maven.compiler.source>
    <maven.compiler.target>25</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <spring-boot.version>4.0.0</spring-boot.version>
    <spring-modulith.version>1.4.0</spring-modulith.version>
    <junit.version>5.11.4</junit.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>${spring-modulith.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.junit</groupId>
            <artifactId>junit-bom</artifactId>
            <version>${junit.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## Règles de Comportement

- **Import de BOM, PAS parent Spring Boot** : préserver le `<pluginManagement>` custom du root
  (compiler `release 25` + `-Xlint:all`, surefire `-Dnet.bytebuddy.experimental=true`,
  failsafe `**/*IT.java`).
- **Artefacts gérés par le BOM → retirer `<version>`** : `spring-boot`,
  `spring-boot-autoconfigure`, `spring-boot-starter-*`, `spring-context`, `spring-web`,
  `spring-jdbc`, `spring-tx`, `spring-orm`, `spring-data-jpa`, `spring-kafka`,
  `spring-modulith-*`. La version vient du BOM.
- **Artefacts NON gérés par le BOM → conserver `<version>`** : vérifier au cas par cas avec
  `mvn dependency:tree`. Si le BOM gère déjà la version (ex. `spring-data-jpa`,
  `jackson-databind` côté géré), retirer ; sinon conserver.
- **`platform-execution-engine`** : retirer `spring-context 6.2.6` (Spring 6) et
  `spring-boot-autoconfigure 4.0.0` → versions héritées du BOM (Spring 7, cohérent Spring Boot 4).
- **`platform-app`** : conserve le `spring-boot-maven-plugin` avec `${spring-boot.version}`
  (plugin non géré par `<dependencyManagement>`). Peut retirer les `<version>` des
  `spring-boot-starter-*` (gérés par le BOM) ; garder la property locale `spring-modulith.version`
  ou s'appuyer sur celle du root.
- **`platform-domain` / `platform-plugin-api`** : ne déclarent aucune dépendance Spring → le
  BOM est sans effet ; **ne pas y toucher**. La pureté ArchUnit reste garantie.
- **Validation à chaque étape** : `mvn clean install` doit rester vert (reactor complet), car
  retirer une version dans un module impacte le classpath via les inter-dépendances.

---

## Dépendances Techniques

```
Ce PDR utilise :
  rien (fondation)

Ce PDR est utilisé par :
  PDR-033 (Conditional Infrastructure Beans) → s'appuie sur un classpath Spring cohérent
  tous les modules → build cohérent après centralisation
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE (ISSUE-132, 133, 134)
- [ ] `mvn dependency:tree` : aucune `<version>` Spring/Boot/Data/Kafka/Modulith explicite dans
      un pom enfant (toutes héritées du BOM)
- [ ] Aucune trace de `spring-context 6.2.6` nulle part (conflit Spring 6/7 éliminé)
- [ ] `mvn clean install` vert sur le reactor complet
- [ ] `.claude/workspace/interfaces-registry.md` : note « versions Spring centralisées (ADR-024) »
</content>

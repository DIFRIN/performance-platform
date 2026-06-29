# ISSUE-132 — Root pom : import du BOM spring-boot-dependencies + spring-modulith

**PDR** : PDR-030
**Module** : root `pom.xml`
**Statut** : DONE
**Priorité** : P0 (bloquant — fondation de toute la centralisation)
**Bloquée par** : —
**Estime** : S (< 1h)

---

## Objectif

Ajouter dans le root `pom.xml` la property `spring-boot.version` et l'import des BOMs
`spring-boot-dependencies` + `spring-modulith-bom` dans `<dependencyManagement>`, **sans**
introduire `spring-boot-starter-parent` comme parent (conserver le `pluginManagement` custom).
Après cette Issue, les versions Spring sont *disponibles* via le BOM ; les modules enfants
seront nettoyés dans ISSUE-133/134.

## Fichiers à Créer / Modifier

```
pom.xml (root)
  └── MODIF : ajout property spring-boot.version + spring-modulith.version ;
             import BOM spring-boot-dependencies + spring-modulith-bom dans
             <dependencyManagement> (junit-bom déjà présent, conservé)
```

## Interfaces à Implémenter

> Configuration Maven (copiée du PDR-030 / ADR-024) :

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

## Règles Spécifiques

- **NE PAS** ajouter `<parent>spring-boot-starter-parent</parent>` : le root garde son
  `<pluginManagement>` (compiler `release 25` + `-Xlint:all`, surefire
  `-Dnet.bytebuddy.experimental=true`, failsafe `**/*IT.java`) — ne pas le modifier.
- Ne pas retirer ni modifier les modules, profils, ou le profil `integration-tests`.
- À ce stade, **ne pas** encore toucher les poms enfants (fait en ISSUE-133/134). Le build
  reste vert car les enfants gardent leurs versions explicites (compatibles 4.0.0 / 7.0.0).

## Critères de Done

- [ ] `mvn clean install -q` → 0 erreur (reactor complet build vert)
- [ ] Le root `pom.xml` importe `spring-boot-dependencies` et `spring-modulith-bom`
- [ ] Aucun parent Spring Boot ; `pluginManagement` custom intact
- [ ] `.claude/workspace/progress.md` : ISSUE-132 → géré par les scripts (`issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (note BOM ADR-024)
</content>

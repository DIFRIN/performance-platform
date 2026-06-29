# ADR-024 — Spring Boot 4 BOM, source unique de vérité des versions Spring

**Date** : 2026-06-29
**Statut** : ACCEPTED
**Décideurs** : Architect (décision utilisateur)
**Priorité** : P0 — BLOQUANT (à traiter avant les autres chantiers de correction).

> Note de numérotation : cet ADR portait initialement le numéro **ADR-022**, en collision
> avec `ADR-022-report-output-local-only`. Il a été renuméroté **ADR-024** ; toute référence
> antérieure à « ADR-022 (BOM Spring) » désigne ce document.

---

## Contexte

Les versions Spring / Spring Boot sont aujourd'hui **hardcodées indépendamment dans chaque
module**, ce qui crée des incohérences dangereuses :

| Module | Version observée |
|---|---|
| root `pom.xml` | **aucun BOM Spring** — uniquement `junit-bom` |
| `platform-execution-engine` | `spring-context 6.2.6` ⚠️ (**Spring 6** — incompatible avec Spring Boot 4 qui embarque Spring 7), `spring-boot-autoconfigure 4.0.0` |
| `platform-infrastructure` | `spring-context/web/jdbc 7.0.0`, `spring-data-jpa 3.4.4`, `spring-boot 4.0.0`, `spring-boot-autoconfigure 4.0.0`, `spring-kafka 4.0.0` |
| `platform-observability` | `spring-boot 4.0.0` |
| `platform-app` | `spring-boot.version=4.0.0` (property locale), pas de parent Spring Boot |

`platform-execution-engine` mélange **Spring 6.2.6** et **Spring Boot 4** dans le même
classpath : c'est un conflit de versions latent (méthodes/contrats divergents entre Spring 6
et 7) qui ne se manifeste qu'à l'exécution.

---

## Décision

**Les versions Spring Boot 4 sont centralisées dans le root `pom.xml` via l'import du BOM
`spring-boot-dependencies`. Les modules enfants ne déclarent plus aucune version Spring /
Spring Boot.**

### 1. BOM importé, PAS parent

On **importe** le BOM dans `<dependencyManagement>` du root — on **n'utilise PAS**
`spring-boot-starter-parent` comme parent. Le root possède déjà un `<pluginManagement>`
custom (compiler `release 25` + `-Xlint:all`, surefire/failsafe configurés) qui entrerait en
conflit avec la configuration de plugins héritée de `spring-boot-starter-parent`.

```xml
<!-- root pom.xml -->
<properties>
    <java.version>25</java.version>
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

### 2. Suppression des versions Spring dans les enfants

Chaque module retire les `<version>` des dépendances **gérées par le BOM**
(`spring-context`, `spring-web`, `spring-jdbc`, `spring-boot`, `spring-boot-autoconfigure`,
`spring-boot-starter-*`, `spring-data-jpa`, `spring-kafka`, `spring-tx`, etc.). La version
vient désormais du BOM.

Les artefacts **non gérés par le BOM** conservent leur `<version>` explicite (ex. HikariCP si
non aligné, Flyway, ArchUnit, WireMock, AssertJ, Mockito, logstash-logback-encoder,
micrometer si version spécifique requise). À vérifier au cas par cas : si le BOM gère déjà la
version, la retirer.

### 3. Correction prioritaire `platform-execution-engine`

`spring-context 6.2.6` → version retirée (gérée par le BOM = Spring 7.x cohérent avec Spring
Boot 4). `spring-boot-autoconfigure 4.0.0` → version retirée.

### 4. Pureté de `platform-domain` et `platform-plugin-api` préservée

`platform-domain` (0 dépendance framework) et `platform-plugin-api` héritent du root **sans
aucun risque** : un BOM importé dans `<dependencyManagement>` ne fait que **contraindre les
versions de dépendances effectivement déclarées** par chaque module. Il **n'ajoute aucune
dépendance** au classpath d'un module qui ne déclare rien. `platform-domain` ne déclarant
aucune dépendance Spring, le BOM est sans effet sur lui : sa pureté (vérifiée par ArchUnit :
0 annotation Spring/JPA/Jackson) reste intégralement garantie.

---

## Justification

- **Source unique de vérité** : une seule property `spring-boot.version=4.0.0` au root pilote
  toutes les versions Spring de tout le multi-module. Plus de dérive possible entre modules.
- **Élimination du conflit Spring 6/7** : `platform-execution-engine` repasse sur Spring 7
  (aligné avec Spring Boot 4) ; fin du mélange de versions latent.
- **BOM importé plutôt que parent** : préserve le `pluginManagement` custom du root
  (compiler `release 25`, surefire/failsafe) sans heritage conflictuel de
  `spring-boot-starter-parent`.
- **Pas d'effet de bord sur les modules purs** : un BOM ne fait que gérer des versions ; il
  n'introduit pas de dépendances. ArchUnit reste vert sur `platform-domain` /
  `platform-plugin-api`.

---

## Conséquences

**Positives** :
- Montée de version Spring Boot future = un seul changement (`spring-boot.version`).
- Cohérence garantie de toutes les versions Spring sur le classpath.
- Conflit Spring 6/7 résolu.

**Négatives / Contraintes** :
- Il faut s'assurer, module par module, que retirer une `<version>` ne casse pas une
  dépendance non gérée par le BOM (vérifier `mvn dependency:tree` après nettoyage).
- `platform-app` doit conserver son `spring-boot-maven-plugin` (repackage) — non géré par le
  `<dependencyManagement>`, donc version via `${spring-boot.version}` du root.

**Fichiers impactés** :
- `pom.xml` (root) — ajout property `spring-boot.version` + import BOMs.
- `platform-execution-engine/pom.xml` — retrait `spring-context 6.2.6` + `spring-boot-autoconfigure 4.0.0`.
- `platform-infrastructure/pom.xml` — retrait des versions Spring/Spring Boot/Spring Data/Spring Kafka gérées par le BOM.
- `platform-observability/pom.xml`, `platform-app/pom.xml`, et tous les autres modules
  déclarant des dépendances Spring — retrait des versions gérées par le BOM.

---

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| `spring-boot-starter-parent` comme parent du root | Conflit avec le `pluginManagement` custom du root (compiler `release 25`, surefire/failsafe). L'import de BOM donne la gestion de versions sans imposer la config de plugins. |
| Garder les versions hardcodées mais les aligner manuellement | Ne supprime pas la dérive future ; chaque montée de version redevient un travail multi-fichiers source d'erreurs. |
| Une property `spring.version` par module | Multiplie les sources de vérité ; exactement le problème actuel. |
</content>
</invoke>

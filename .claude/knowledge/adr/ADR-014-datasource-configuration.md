# ADR-014 — Configuration Technique des Datasources

**Date** : 2026-06-15
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : ISSUE-040 (DatabaseTaskExecutor). La spec 03 §6 définit une structure
`datasources:` dans `application.yaml` mais ne dit jamais comment elle alimente le
`DatasourceProvider`. Le Developer n'a pas de réponse claire : binding via
`@ConfigurationProperties` ? `@Bean` manuel ? Et où la config JDBC doit-elle vivre —
dans le YAML de scénario (versionné dans Git) ou dans `application.yaml` ?

---

## Contexte

Le DSL de scénario référence une datasource par un **nom logique** :

```yaml
parameters:
  datasource: customer-db
```

La connexion JDBC réelle (URL, credentials, pool) n'est pas dans le scénario. Deux
questions restent ouvertes :

1. **Où** déclare-t-on la config JDBC technique ?
2. **Comment** le code résout-il `"customer-db"` → `DataSource` ?

Contrainte forte : les **credentials ne doivent jamais apparaître dans le YAML de
scénario**, qui est versionné dans Git (CNF-03).

## Décision

### 1. Lieu de déclaration — Option C

**Les datasources sont déclarées dans `application.yaml` sous la clé
`platform.datasources.<nom-logique>`. Le YAML de scénario ne contient que le nom
logique.** Les credentials passent obligatoirement par variables d'environnement,
selon la convention ADR-006 (`${ENV_VAR:default}`).

```yaml
# application.yaml — configuration technique (NON versionnée avec les credentials réels)
platform:
  datasources:
    customer-db:
      url: jdbc:postgresql://localhost:5432/customers
      username: ${DB_USER:postgres}
      password: ${DB_PASSWORD:changeme}
      driver-class-name: org.postgresql.Driver
      hikari:
        maximum-pool-size: 10
        minimum-idle: 2
        connection-timeout: 30000
    warehouse-db:
      url: jdbc:postgresql://warehouse:5432/wh
      username: ${WH_USER:postgres}
      password: ${WH_PASSWORD:changeme}
      driver-class-name: org.postgresql.Driver
```

```yaml
# scenario.yaml — référence LOGIQUE uniquement, aucun credential
steps:
  - id: purge-db
    task: database
    phase: PREPARATION
    parameters:
      operation: PURGE
      datasource: customer-db      # ← résolu contre platform.datasources.customer-db
      table: orders
  - id: seed-db
    task: database
    phase: PREPARATION
    parameters:
      operation: POPULATE
      datasource: customer-db
      scriptPath: classpath:sql/seed-customers.sql
```

### 2. Mécanisme de résolution — Sous-option C1

**On garde `DatasourceProvider` comme registre logique, alimenté par un `@Bean` de
configuration qui binde `platform.datasources.*` via `@ConfigurationProperties` et
construit un `HikariDataSource` par entrée.**

```java
// platform-infrastructure/.../executor/database/PlatformDatasourcesProperties.java
@ConfigurationProperties(prefix = "platform")
public record PlatformDatasourcesProperties(
        Map<String, DatasourceProperties> datasources
) {
    public record DatasourceProperties(
            String url,
            String username,
            String password,
            String driverClassName,
            HikariProperties hikari
    ) {}

    public record HikariProperties(
            Integer maximumPoolSize,
            Integer minimumIdle,
            Long connectionTimeout
    ) {}
}
```

```java
// platform-infrastructure/.../executor/database/DatasourceConfiguration.java
@Configuration
@EnableConfigurationProperties(PlatformDatasourcesProperties.class)
public class DatasourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DatasourceConfiguration.class);

    @Bean
    public DatasourceProvider datasourceProvider(PlatformDatasourcesProperties props) {
        DatasourceProvider provider = new DatasourceProvider();
        if (props.datasources() == null || props.datasources().isEmpty()) {
            log.warn("action=no_datasource_configured prefix=platform.datasources");
            return provider;
        }
        props.datasources().forEach((name, ds) -> {
            HikariDataSource hikari = new HikariDataSource();
            hikari.setPoolName("hikari-" + name);
            hikari.setJdbcUrl(ds.url());
            hikari.setUsername(ds.username());
            hikari.setPassword(ds.password());
            if (ds.driverClassName() != null) {
                hikari.setDriverClassName(ds.driverClassName());
            }
            if (ds.hikari() != null) {
                if (ds.hikari().maximumPoolSize() != null) hikari.setMaximumPoolSize(ds.hikari().maximumPoolSize());
                if (ds.hikari().minimumIdle() != null)     hikari.setMinimumIdle(ds.hikari().minimumIdle());
                if (ds.hikari().connectionTimeout() != null) hikari.setConnectionTimeout(ds.hikari().connectionTimeout());
            }
            provider.register(name, hikari);
            log.info("action=datasource_configured name={} url={}", name, ds.url());
        });
        return provider;
    }
}
```

`DatasourceProvider` **n'est plus un `@Component`** : il est instancié par le `@Bean`
ci-dessus (retirer `@Component` et l'import `org.springframework.stereotype.Component`).
Le `DatabaseTaskExecutor` continue d'injecter `DatasourceProvider` et de résoudre par
nom (aucun changement de son constructeur ni de sa résolution `datasourceProvider.get(name)`).

> `DatasourceProvider` reste utile : il découple la résolution par nom logique de
> l'implémentation Hikari, gère la collision (warning sur re-register), et offre un
> point d'extension si un plugin externe veut enregistrer une datasource. On ne le
> supprime pas.

## Justification

- **Sécurité (CNF-03)** : avec l'Option C, aucun credential ne touche le YAML de
  scénario versionné. Les credentials passent par env vars (`${DB_USER}`/`${DB_PASSWORD}`),
  cohérent avec ADR-006 et les K8s Secrets (CD-02).
- **Convention Spring Boot** : multi-datasource via `@ConfigurationProperties(prefix=...)`
  + `Map` est le pattern Spring Boot idiomatique pour des datasources nommées
  dynamiquement, sans connaître les noms à la compilation.
- **Pourquoi C1 et pas C2** : Spring Boot n'auto-configure pas une
  `Map<String, DataSource>` à partir de `platform.datasources.*`. C2 obligerait à
  déclarer un `@Bean` nommé par datasource, avec des bean names contenant des tirets
  (`customer-db`) — problématique et figé à la compilation (impossible d'ajouter une
  datasource par simple config). C1 binde une `Map` dynamique : ajouter une datasource
  = ajouter une entrée YAML, zéro code.
- **Auto-suffisance partielle assumée** : le scénario n'est pas auto-suffisant (besoin
  d'`application.yaml`), mais c'est le bon compromis — le scénario décrit *quoi* faire,
  l'environnement décrit *où* (URL/credentials propres à chaque environnement).

## Conséquences

**Positives** :
- Credentials hors du scénario Git → conforme CNF-03.
- Ajout d'une datasource = pure config YAML, aucun code (CF-04 esprit).
- Pool HikariCP par datasource → conforme CNF-01 (I/O concurrent, 100+ agents).
- Résolution nom logique → `DataSource` sans ambiguïté pour le Developer.

**Négatives / Contraintes** :
- Le scénario nécessite un `application.yaml` cohérent : un `datasource: customer-db`
  référençant une entrée absente échoue à l'exécution (déjà géré : `fail(...)` si
  `datasourceProvider.get(name) == null`).
- Désactiver l'auto-config Spring Boot `DataSourceAutoConfiguration` si le starter JDBC
  complet était présent (ici on n'utilise que `spring-jdbc` + `HikariCP` — cf. ADR-013,
  pas de starter, donc pas d'auto-config concurrente à exclure).
- `DatasourceProvider` perd `@Component` → bien vérifier qu'aucun autre point n'attend
  un scan automatique de ce bean (il est désormais fourni par `DatasourceConfiguration`).

**Dépendances Maven** : voir ADR-013 (`spring-jdbc` + `HikariCP`). Aucune dépendance
supplémentaire propre à ADR-014.

**Fichiers impactés** :
- `platform-infrastructure/.../executor/database/PlatformDatasourcesProperties.java` (nouveau).
- `platform-infrastructure/.../executor/database/DatasourceConfiguration.java` (nouveau).
- `platform-infrastructure/.../executor/database/DatasourceProvider.java` — retirer `@Component`.
- `platform-app/.../application.yaml` — bloc `platform.datasources` (clé renommée :
  `datasources` → `platform.datasources`).
- `.claude/knowledge/specs/03-task-framework.md` — section 6 réécrite.

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| **Option A** (tout dans `application.yaml`, sans nom logique dans le scénario) | Le scénario doit référencer une datasource ; un nom logique est indispensable. A et C convergent en pratique — C formalise la référence logique. |
| **Option B** (config JDBC complète dans le scénario) | Credentials en clair dans le YAML versionné Git → viole CNF-03. Pas de réutilisation entre steps. Rejet ferme. |
| **Sous-option C2** (`Map<String, DataSource>` auto-injectée) | Spring Boot n'auto-configure pas cette Map depuis `platform.datasources.*`. Imposerait des bean names à tirets figés à la compilation. Pas de datasource ajoutable par simple config. |
| Clé YAML racine `datasources:` (sans préfixe `platform`) | Risque de collision avec d'éventuelles conventions Spring Boot et manque de namespace. `platform.datasources` est cohérent avec `platform.plugins`. |

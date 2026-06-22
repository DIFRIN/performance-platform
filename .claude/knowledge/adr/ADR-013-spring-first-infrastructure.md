# ADR-013 — Règle Spring-first pour l'Infrastructure

**Date** : 2026-06-15
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : ISSUE-040 (DatabaseTaskExecutor). L'opération POPULATE lit et découpe
un script SQL à la main (`sql.split(";")`), lit les ressources classpath/filesystem
manuellement (`getResourceAsStream`, `Files.readString`), et gère un registre custom
de `DataSource`. Spring Boot fournit nativement des composants équivalents, plus
robustes et déjà testés. Une règle ferme est nécessaire pour éviter la prolifération
de code custom redondant dans toute la couche infrastructure.

---

## Contexte

`platform-infrastructure` est la couche des adapters techniques. Aujourd'hui elle
ne déclare que `spring-context` et réimplémente à la main des mécanismes que Spring
Boot offre déjà :

- Découpage et exécution de scripts SQL → réinventé via `String.split(";")`, ce qui
  casse sur les `;` à l'intérieur de littéraux, les commentaires `--` / `/* */`,
  les blocs `BEGIN ... END`, et l'encodage.
- Lecture de ressources `classpath:` / filesystem → réinventée via `getResourceAsStream`
  et `Files.readString`, sans abstraction `Resource`.
- Registre de `DataSource` → réinventé via une `Map` custom (`DatasourceProvider`)
  sans pool de connexions (HikariCP).

Ce code custom est une dette : moins robuste que Spring, non couvert par les tests
de Spring, et source de bugs subtils (cf. le `split(";")`).

## Décision

**Nous décidons d'imposer la règle Spring-first comme contrainte ferme (ADR), pas
comme simple recommandation.**

> Pour tout composant de la couche `platform-infrastructure` (et de tout module hors
> `platform-domain` / `platform-plugin-api`), il faut d'abord vérifier si Spring ou
> Spring Boot fournit un composant configurable équivalent. On ne code custom que ce
> que Spring n'offre pas directement.

Cette règle ne s'applique **jamais** à `platform-domain` ni `platform-plugin-api`,
qui restent à 0 dépendance Spring (ADR-004, CF-08).

### Composants Spring à utiliser — Table de référence

| Besoin | À utiliser (Spring) | NE PAS coder à la main |
|---|---|---|
| Exécuter un script SQL | `org.springframework.jdbc.datasource.init.ResourceDatabasePopulator` | `sql.split(";")` + boucle `Statement.execute` |
| Lire une ressource `classpath:`/`file:` | `DefaultResourceLoader` / `ResourceLoader.getResource(path)` → `Resource` | `getResourceAsStream` + `Files.readString` |
| Pool de connexions JDBC | `HikariDataSource` (via `spring-boot-starter-jdbc`, Hikari transitif) | `DriverManager.getConnection` ad hoc, pool maison |
| Binding YAML → objet Java | `@ConfigurationProperties` (records) | parsing manuel de `Map<String,Object>` |
| Opérations SQL simples (COUNT, EXISTS) | `JdbcTemplate` / `JdbcClient` | `Connection` + `PreparedStatement` + boucle `ResultSet` manuelle |
| Gestion de transaction | `TransactionTemplate` / `@Transactional` | `conn.setAutoCommit(false)` + `commit/rollback` manuels |

### Application immédiate (ISSUE-040)

Le `DatabaseTaskExecutor` doit être refactoré :

**POPULATE** — remplacer le bloc `readScript` + `split(";")` par :

```java
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

private final ResourceLoader resourceLoader = new DefaultResourceLoader();

private TaskResult executePopulate(StepDefinition step, long startNanos, DataSource ds) {
    String scriptPath = (String) step.parameters().get("scriptPath");
    if (scriptPath == null || scriptPath.isBlank()) {
        return fail(step, startNanos, "Required parameter 'scriptPath' is missing or blank", null);
    }
    // DefaultResourceLoader gère "classpath:" et les chemins filesystem nativement.
    Resource script = resourceLoader.getResource(scriptPath);
    if (!script.exists()) {
        return fail(step, startNanos, "Script not found: " + scriptPath, null);
    }

    var populator = new ResourceDatabasePopulator(script);
    populator.setSeparator(";");
    populator.setCommentPrefixes("--");
    populator.setContinueOnError(false);

    try (Connection conn = ds.getConnection()) {
        populator.populate(conn);   // gère séparateurs, commentaires, encodage UTF-8
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        // ResourceDatabasePopulator ne retourne pas de rowsAffected — output = scriptExecuted
        Map<String, Object> outputs = Map.of(
                "scriptExecuted", scriptPath,
                "duration", formatDuration(elapsed)
        );
        return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);
    } catch (Exception e) {
        return fail(step, startNanos, "POPULATE failed on '" + scriptPath + "': " + e.getMessage(), e);
    }
}
```

> Note : `ResourceDatabasePopulator` n'expose pas de `rowsAffected` agrégé. L'output
> `rowsAffected` est remplacé par `scriptExecuted` pour POPULATE. PURGE conserve son
> `rowsAffected` (un seul `DELETE`, `executeUpdate` suffit, pas besoin de Spring ici).

**PURGE** — peut rester en JDBC pur (un seul `DELETE`, pas de gain Spring), OU
utiliser `JdbcTemplate.update("DELETE FROM " + table)`. Les deux sont acceptés ;
`JdbcTemplate` est préféré pour la cohérence et la gestion d'exceptions.

## Justification

- **Robustesse** : `ResourceDatabasePopulator` gère correctement les séparateurs, les
  commentaires SQL (`--`, `/* */`), les blocs procéduraux et l'encodage — autant de
  cas que `split(";")` casse silencieusement.
- **Moins de code à maintenir et tester** : `readScript` (gestion classpath/filesystem)
  et le découpage SQL disparaissent. Moins de surface de bug, coverage plus facile.
- **Cohérence** : un seul mécanisme de chargement de ressources (`ResourceLoader`)
  dans toute la plateforme.
- **Pool de connexions** : HikariCP via `spring-boot-starter-jdbc` remplace l'absence
  de pool actuelle — nécessaire pour CNF-01 (100+ agents, I/O concurrent).
- C'est la convention idiomatique Spring Boot ; un nouveau Developer la reconnaît.

## Conséquences

**Positives** :
- Suppression du code custom `readScript` et du `split(";")` dans `DatabaseTaskExecutor`.
- Robustesse accrue sur le parsing SQL (cas réels couverts par Spring).
- Pool de connexions HikariCP disponible pour tous les executors DB.
- Règle réutilisable pour les futurs executors (KAFKA via `KafkaTemplate`, HTTP via
  `RestClient`/`WebClient`, MOCK_SERVER via WireMock Spring Boot).

**Négatives / Contraintes** :
- Nouvelles dépendances Maven dans `platform-infrastructure` (cf. ci-dessous) →
  empreinte mémoire légèrement accrue (acceptable vs CNF-05).
- `spring-boot-starter-jdbc` tire l'auto-configuration Spring Boot ; il faut veiller
  à ne pas activer le `DataSourceAutoConfiguration` par défaut (on construit nos
  `HikariDataSource` nommés nous-mêmes — cf. ADR-014).
- POPULATE perd l'output `rowsAffected` (remplacé par `scriptExecuted`) — impact sur
  les assertions qui liraient `rowsAffected` après un POPULATE (à documenter en spec).

**Dépendances Maven à ajouter** (`platform-infrastructure/pom.xml`) :

```xml
<!-- Spring JDBC : ResourceDatabasePopulator, JdbcTemplate, DataSourceUtils -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-jdbc</artifactId>
    <version>7.0.0</version>
</dependency>
<!-- HikariCP : pool de connexions (datasources nommées, cf. ADR-014) -->
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>6.2.1</version>
</dependency>
```

> `spring-jdbc` apporte `spring-core` (donc `DefaultResourceLoader`, `Resource`)
> transitivement, déjà présent via `spring-context`. `HikariCP` est ajouté
> explicitement plutôt que via `spring-boot-starter-jdbc` pour éviter de tirer toute
> l'auto-configuration Spring Boot dans une couche qui n'en a pas besoin (le module
> ne dépend que de `spring-context`/`spring-jdbc`, pas du starter complet). Si un
> futur besoin justifie le starter, un nouvel ADR le tranchera.

**Fichiers impactés** :
- `platform-infrastructure/pom.xml` — ajout `spring-jdbc` + `HikariCP`.
- `platform-infrastructure/.../executor/database/DatabaseTaskExecutor.java` —
  refacto POPULATE (et idéalement PURGE).
- `.claude/knowledge/specs/03-task-framework.md` — section 8 (Spring-first) + section 6.
- `.claude/knowledge/constraints.md` — nouvelle contrainte CC-05.

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Simple recommandation (pas d'ADR) | Sans règle ferme, le code custom continue de proliférer. Le Reviewer n'a pas de base pour refuser une réimplémentation maison. |
| Garder `split(";")` + `readScript` | Fragile (commentaires, littéraux, encodage), non testé par Spring, dette technique. |
| `spring-boot-starter-jdbc` complet | Tire l'auto-config Spring Boot (`DataSourceAutoConfiguration`) non désirée dans cette couche ; `spring-jdbc` + `HikariCP` explicites suffisent et restent minimaux. |

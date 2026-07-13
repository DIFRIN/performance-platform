# PDR-042 — DatabaseTaskExecutor Refinement

**Module Maven** : `platform-infrastructure`
**Package** : `com.performance.platform.infrastructure.executor.database`
**Status** : WAITING
**Specs de reference** : `.claude/knowledge/specs/03-task-framework.md`, ADR-013, ADR-014
**Depend de** : PDR-010 (DatabaseTaskExecutor — déjà DONE)
**Issues** : ISSUE-166, ISSUE-167

---

## Responsabilite

Simplifier le `DatabaseTaskExecutor` en ajoutant le support des requêtes SQL inline (paramètre `query`) en complément du support existant par fichier script (`scriptPath`). La configuration de la datasource reste dans `application*.yaml` (ADR-014), le step YAML référence le nom logique.

Ce PDR formalise le contrat YAML et aligne l'implémentation avec ADR-013 (Spring-first : `JdbcTemplate`, `ResourceDatabasePopulator`).

---

## Contrat YAML — Scenario Step

### Opérations supportées

```yaml
# Opération 1 : PURGE — supprimer toutes les lignes d'une table
- id: purge-orders
  task: database
  phase: PREPARATION
  parameters:
    operation: PURGE
    datasource: app-db            # nom logique (ADR-014)
    table: orders

# Opération 2 : POPULATE — exécuter un script SQL depuis un fichier
- id: populate-test-data
  task: database
  phase: PREPARATION
  parameters:
    operation: POPULATE
    datasource: app-db
    scriptPath: classpath:db/testdata/orders.sql   # classpath: ou chemin absolu

# Opération 3 : QUERY — exécuter une requête SQL inline (NOUVEAU)
- id: count-active-users
  task: database
  phase: PREPARATION
  parameters:
    operation: QUERY
    datasource: app-db
    query: "SELECT COUNT(*) FROM users WHERE status = 'ACTIVE'"
    queryType: SELECT            # SELECT (retourne résultat) ou UPDATE (retourne rowsAffected)

# Opération 4 : QUERY — INSERT/UPDATE/DELETE inline (NOUVEAU)
- id: cleanup-stale-sessions
  task: database
  phase: PREPARATION
  parameters:
    operation: QUERY
    datasource: app-db
    query: "DELETE FROM sessions WHERE last_access < NOW() - INTERVAL '1 hour'"
    queryType: UPDATE            # retourne rowsAffected
```

### Paramètres par opération

| Paramètre | PURGE | POPULATE | QUERY |
|---|---|---|---|
| `operation` | OBLIGATOIRE | OBLIGATOIRE | OBLIGATOIRE |
| `datasource` | OBLIGATOIRE | OBLIGATOIRE | OBLIGATOIRE |
| `table` | OBLIGATOIRE | — | — |
| `scriptPath` | — | OBLIGATOIRE | — |
| `query` | — | — | OBLIGATOIRE |
| `queryType` | — | — | OPTIONNEL (défaut: UPDATE) |

---

## Implémentation

### DatabaseTaskExecutor — ajout du case QUERY

```java
// Dans execute() — ajout du case :
case "QUERY" -> executeQuery(step, startNanos, ds);
```

### executeQuery (nouvelle méthode)

```java
/**
 * Exécute une requête SQL inline.
 * <p>
 * Pour {@code queryType=SELECT} : exécute via {@link JdbcTemplate#queryForList}
 * et retourne les résultats dans {@code outputs.rows}.
 * <p>
 * Pour {@code queryType=UPDATE} (default) : exécute via {@link JdbcTemplate#update}
 * et retourne le nombre de lignes affectées dans {@code outputs.rowsAffected}.
 * <p>
 * Utilise {@link JdbcTemplate} (ADR-013 Spring-first).
 */
private TaskResult executeQuery(StepDefinition step, long startNanos, DataSource ds) {
    String query = (String) step.parameters().get("query");
    if (query == null || query.isBlank()) {
        return fail(step, startNanos, "Required parameter 'query' is missing or blank for QUERY operation", null);
    }

    String queryType = Objects.toString(step.parameters().get("queryType"), "UPDATE").toUpperCase().trim();

    try {
        log.info("action=query_start queryType={} datasource={} stepId={}",
                queryType, step.parameters().get("datasource"), step.id().value());

        var jdbc = new JdbcTemplate(ds);

        if ("SELECT".equals(queryType)) {
            List<Map<String, Object>> rows = jdbc.queryForList(query);
            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    "rows", rows,
                    "rowCount", rows.size(),
                    "duration", formatDuration(elapsed)
            );
            log.info("action=query_done queryType=SELECT rowCount={} duration={} stepId={}",
                    rows.size(), formatDuration(elapsed), step.id().value());
            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);
        } else {
            int rowsAffected = jdbc.update(query);
            var elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            Map<String, Object> outputs = Map.of(
                    "rowsAffected", rowsAffected,
                    "duration", formatDuration(elapsed)
            );
            log.info("action=query_done queryType=UPDATE rowsAffected={} duration={} stepId={}",
                    rowsAffected, formatDuration(elapsed), step.id().value());
            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);
        }
    } catch (Exception e) {
        log.error("action=query_failed queryType={} stepId={}", queryType, step.id().value(), e);
        return fail(step, startNanos, "QUERY failed: " + e.getMessage(), e);
    }
}
```

---

## Fichiers à Modifier

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/database/
  └── DatabaseTaskExecutor.java   — ajouter executeQuery() + case "QUERY" dans execute()

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/database/
  └── DatabaseTaskExecutorTest.java — ajouter tests QUERY SELECT et UPDATE
```

---

## Tests (ISSUE-167)

Tests à ajouter dans `DatabaseTaskExecutorTest` :

1. `shouldExecuteSelectQuery` — QUERY + queryType=SELECT → retourne rows dans outputs
2. `shouldExecuteUpdateQuery` — QUERY + queryType=UPDATE → retourne rowsAffected
3. `shouldDefaultToUpdateWhenNoQueryType` — QUERY sans queryType → UPDATE par défaut
4. `shouldFailOnMissingQuery` — QUERY sans paramètre query → FAILED
5. `shouldFailOnUnknownOperation` — operation inconnue → FAILED (test existant, à conserver)
6. `shouldFailOnMissingDatasourceForQuery` — QUERY sans datasource → FAILED

---

## Règles Spécifiques

- `queryType` par défaut = `"UPDATE"` si non spécifié (sécurité : ne pas exposer de données par accident)
- Pour `SELECT` : les résultats sont dans `outputs.rows` (List<Map<String, Object>>) + `outputs.rowCount`
- Pour `UPDATE` : le résultat est dans `outputs.rowsAffected` (int)
- La requête est exécutée telle quelle — pas de validation anti-injection (c'est un outil de test, pas une API publique)
- Timeout via `step.timeout()` comme les autres opérations
- Virtual Threads pour l'exécution (comme PURGE et POPULATE)
- Le `@Preparation` annotation existante couvre déjà QUERY (pas besoin d'une nouvelle annotation)
- La datasource est résolue via `DatasourceProvider` (existant, ADR-014)

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-010 (DatabaseTaskExecutor) → déjà DONE
  ADR-013 (Spring-first infrastructure)
  ADR-014 (Datasource configuration)

Ce PDR est utilisé par :
  (aucun)
```

---

## Criteres de Done (PDR complet)

- [ ] `executeQuery()` implémentée avec support SELECT et UPDATE (ISSUE-166)
- [ ] `case "QUERY"` ajouté dans le switch de `execute()` (ISSUE-166)
- [ ] Tests unitaires pour QUERY SELECT et UPDATE (ISSUE-167)
- [ ] Tests unitaires pour cas d'erreur (missing query, unknown operation) (ISSUE-167)
- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] La Javadoc du `DatabaseTaskExecutor` mentionne les 3 opérations supportées

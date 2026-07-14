# ISSUE-166 — DatabaseTaskExecutor: Add QUERY Operation

**PDR** : PDR-042
**Module** : `platform-infrastructure`
**Statut** : APPROVED
**Priorite** : P1 (nouvelle fonctionnalité)
**Bloquee par** : —
**Estime** : M (1-2h)

---

## Objectif

Ajouter l'opération `QUERY` au `DatabaseTaskExecutor` pour permettre l'exécution de requêtes SQL inline (paramètre `query`) en plus des opérations existantes `PURGE` (DELETE FROM table) et `POPULATE` (script file).

## Fichiers à Modifier

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/database/
  └── DatabaseTaskExecutor.java   — ajouter executeQuery() + case "QUERY"
```

## Implémentation

### executeQuery (nouvelle méthode privée)

```java
/**
 * Exécute une requête SQL inline.
 * <p>
 * {@code queryType=SELECT} : retourne les résultats dans {@code outputs.rows}
 *   et le compte dans {@code outputs.rowCount}.
 * <p>
 * {@code queryType=UPDATE} (défaut) : retourne le nombre de lignes affectées
 *   dans {@code outputs.rowsAffected}.
 */
private TaskResult executeQuery(StepDefinition step, long startNanos, DataSource ds) {
    String query = (String) step.parameters().get("query");
    if (query == null || query.isBlank()) {
        return fail(step, startNanos,
                "Required parameter 'query' is missing or blank for QUERY operation", null);
    }

    String queryType = Objects.toString(
            step.parameters().get("queryType"), "UPDATE").toUpperCase().trim();

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
        log.error("action=query_failed queryType={} stepId={}",
                queryType, step.id().value(), e);
        return fail(step, startNanos, "QUERY failed: " + e.getMessage(), e);
    }
}
```

### Modification de execute() — ajout du case

Dans la méthode `execute()`, dans le switch sur `operation`, ajouter :

```java
case "QUERY" -> executeQuery(step, startNanos, ds);
```

Le switch devient :

```java
return switch (operation) {
    case "PURGE" -> executePurge(step, startNanos, ds);
    case "POPULATE" -> executePopulate(step, startNanos, ds);
    case "QUERY" -> executeQuery(step, startNanos, ds);
    default -> fail(step, startNanos, "Unknown database operation: " + operation, null);
};
```

### Mise à jour Javadoc de la classe

Ajouter dans la Javadoc de `DatabaseTaskExecutor` :

```java
 * Paramètres de step :
 * <ul>
 *   <li>{@code operation} — obligatoire : PURGE, POPULATE, QUERY</li>
 *   <li>{@code datasource} — obligatoire : nom logique de la datasource</li>
 *   <li>{@code table} — obligatoire pour PURGE : nom de la table à vider</li>
 *   <li>{@code scriptPath} — obligatoire pour POPULATE : chemin du script SQL</li>
 *   <li>{@code query} — obligatoire pour QUERY : requête SQL inline</li>
 *   <li>{@code queryType} — optionnel pour QUERY : SELECT (retourne rows) ou UPDATE (défaut, retourne rowsAffected)</li>
 * </ul>
```

## Règles Spécifiques

- `queryType` par défaut = `"UPDATE"` si non spécifié (sécurité par défaut : ne pas exposer de données)
- Pour `SELECT` : `outputs.rows` est une `List<Map<String, Object>>` — chaque Map est une ligne (colonne → valeur)
- Pour `UPDATE` : `outputs.rowsAffected` est un `int`
- La requête est exécutée telle quelle via `JdbcTemplate` (ADR-013 Spring-first)
- Timeout via `step.timeout()` comme PURGE et POPULATE
- Virtual Threads pour l'exécution bloquante (via le `executor.submit()` existant)
- Pas de validation anti-injection — c'est un outil de test, pas une API publique
- L'annotation `@Preparation(name = "database")` existante couvre déjà QUERY

## Criteres de Done

- [ ] `executeQuery()` implémentée avec support SELECT et UPDATE
- [ ] `case "QUERY"` ajouté dans le switch de `execute()`
- [ ] Javadoc de la classe mise à jour avec les 5 paramètres
- [ ] `mvn compile -pl platform-infrastructure -q` → 0 erreur
- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur (tests existants non cassés)

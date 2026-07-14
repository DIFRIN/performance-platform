# ISSUE-167 — DatabaseTaskExecutor QUERY Operation Tests

**PDR** : PDR-042
**Module** : `platform-infrastructure`
**Statut** : DONE
**Priorite** : P1 (tests pour ISSUE-166)
**Bloquee par** : ISSUE-166 (executeQuery implémentée)
**Estime** : M (1-2h)

---

## Objectif

Ajouter les tests unitaires pour la nouvelle opération `QUERY` du `DatabaseTaskExecutor`.

## Fichiers à Modifier

```
platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/database/
  └── DatabaseTaskExecutorTest.java   — ajouter tests QUERY
```

## Tests à Ajouter

### 1. shouldExecuteSelectQuery

```java
@Test
void shouldExecuteSelectQuery() {
    // Given: H2 in-memory, table créée avec 3 lignes
    // When: operation=QUERY, query="SELECT COUNT(*) FROM test_table", queryType=SELECT
    // Then: TaskResult SUCCESS
    //       outputs.rows.size() == 1 (une ligne avec le count)
    //       outputs.rowCount == 1
}
```

### 2. shouldExecuteUpdateQuery

```java
@Test
void shouldExecuteUpdateQuery() {
    // Given: H2 in-memory, table créée avec des données
    // When: operation=QUERY, query="UPDATE test_table SET value = 'updated'", queryType=UPDATE
    // Then: TaskResult SUCCESS
    //       outputs.rowsAffected > 0
}
```

### 3. shouldDefaultToUpdateWhenNoQueryType

```java
@Test
void shouldDefaultToUpdateWhenNoQueryType() {
    // Given: H2 in-memory
    // When: operation=QUERY, query="DELETE FROM test_table", pas de queryType
    // Then: TaskResult SUCCESS (UPDATE par défaut)
    //       outputs.rowsAffected >= 0
}
```

### 4. shouldFailOnMissingQuery

```java
@Test
void shouldFailOnMissingQuery() {
    // When: operation=QUERY, datasource=app-db, pas de query
    // Then: TaskResult FAILED
    //       errorMessage contient "Required parameter 'query'"
}
```

### 5. shouldFailOnMissingDatasourceForQuery

```java
@Test
void shouldFailOnMissingDatasourceForQuery() {
    // When: operation=QUERY, query="SELECT 1", pas de datasource
    // Then: TaskResult FAILED
    //       errorMessage contient "Required parameter 'datasource'"
}
```

### 6. shouldFailOnUnknownOperation

```java
@Test
void shouldFailOnUnknownOperation() {
    // Test existant — vérifier qu'il passe toujours après l'ajout de QUERY
    // When: operation=INVALID_OP
    // Then: TaskResult FAILED
}
```

## Setup de test

Le `DatabaseTaskExecutorTest` utilise probablement déjà H2 (base en mémoire) ou mocks `DatasourceProvider`. Les nouveaux tests doivent suivre la même convention.

Si H2 :
```java
@BeforeEach
void setUp() {
    // Créer une table test_table avec quelques lignes
    jdbcTemplate.execute("CREATE TABLE test_table (id INT, value VARCHAR)");
    jdbcTemplate.execute("INSERT INTO test_table VALUES (1, 'a')");
    jdbcTemplate.execute("INSERT INTO test_table VALUES (2, 'b')");
    jdbcTemplate.execute("INSERT INTO test_table VALUES (3, 'c')");
}
```

## Règles Spécifiques

- Tests unitaires uniquement (H2 in-memory, pas de Testcontainers PostgreSQL)
- Les tests existants (PURGE, POPULATE) ne doivent PAS être modifiés
- Chaque test vérifie les outputs spécifiques (rows, rowCount, rowsAffected, duration)
- `queryType` est optionnel → tester le défaut UPDATE
- `query` est obligatoire pour QUERY → tester l'absence

## Criteres de Done

- [ ] 6 nouveaux tests ajoutés à `DatabaseTaskExecutorTest`
- [ ] Tous les tests existants passent (PURGE, POPULATE, erreurs)
- [ ] `mvn test -pl platform-infrastructure -Dtest=DatabaseTaskExecutorTest -q` → 0 erreur
- [ ] Couverture des cas d'erreur (missing query, missing datasource)
- [ ] Couverture des deux queryType (SELECT, UPDATE)

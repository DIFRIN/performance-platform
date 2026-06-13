# ISSUE-061 — DatabaseAssertionExecutor (@Assertion name="database")

**PDR** : PDR-014
**Module** : `platform-assertion`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-059
**Estime** : M

---

## Objectif

Implémenter `DatabaseAssertionExecutor` : exécute une requête SQL de comptage et compare le
résultat avec un opérateur.

## Fichiers à Créer

```
platform-assertion/src/main/java/com/performance/platform/assertion/database/
  └── DatabaseAssertionExecutor.java

platform-assertion/src/test/java/com/performance/platform/assertion/database/
  └── DatabaseAssertionExecutorIT.java   — Testcontainers PostgreSQL
```

## Interfaces à Implémenter

```java
@Assertion(name = "database", description = "SQL count/exists assertions")
public class DatabaseAssertionExecutor implements AssertionExecutor {
    public String getSupportedAssertionName() { return "database"; }
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) { /* ... */ }
}
```

## Règles Spécifiques

- Params : `datasource`, `query`, `operator`, `value`, `unit`.
- Exécute la requête, récupère la valeur numérique (COUNT), compare via `AssertionOperator`.
- `Evidence` : actual=résultat SQL, expected=value.
- Erreur SQL → `AssertionStatus.ERROR`.
- I/O sous Virtual Threads.

## Critères de Done

- [ ] `mvn verify -pl platform-assertion -P integration-tests` → IT passe
- [ ] `COUNT(*) > N` évalué correctement sur PostgreSQL Testcontainers
- [ ] `.claude/progress.md` mis à jour : ISSUE-061 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `DatabaseAssertionExecutor` → STABLE

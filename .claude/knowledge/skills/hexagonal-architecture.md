# Skill — Hexagonal Architecture

> Ce skill décrit les patterns et règles à suivre pour l'architecture hexagonale.
> Lire avant d'implémenter tout nouveau composant.

---

## Structure de Package par Module

```
com.performance.platform.<module>/
├── domain/
│   ├── model/          ← entités, value objects, aggregates (records Java 25)
│   ├── service/        ← domain services (logique métier sans I/O)
│   └── event/          ← domain events
├── application/
│   ├── port/
│   │   ├── in/         ← interfaces use cases (driving ports)
│   │   └── out/        ← interfaces I/O (driven ports)
│   ├── command/        ← command objects
│   ├── query/          ← query objects
│   └── handler/        ← CommandHandler, QueryHandler
└── infrastructure/
    ├── adapter/
    │   ├── in/         ← REST controllers, event listeners (driving adapters)
    │   └── out/        ← JPA repos, HTTP clients, message publishers (driven adapters)
    ├── persistence/    ← JPA entities, mappers
    └── config/         ← Spring @Configuration beans
```

---

## Règles d'Import (Violations = Build Failure)

```
domain    → interdit d'importer : spring, jpa, jackson, kafka, gatling
application → interdit d'importer : spring beans, jpa, infrastructure classes
              autorisé : domain classes, java.*, slf4j
infrastructure → autorisé : tout
```

---

## Exemple Port Entrant (Use Case)

```java
// application/port/in/ExecuteScenarioUseCase.java
public interface ExecuteScenarioUseCase {
    ExecutionId execute(ExecuteScenarioCommand command);
}

// application/command/ExecuteScenarioCommand.java
public record ExecuteScenarioCommand(
    ScenarioDefinition scenario,
    Map<String, String> overrides
) {}
```

---

## Exemple Port Sortant (Repository)

```java
// application/port/out/ExecutionRepository.java
public interface ExecutionRepository {
    void save(ExecutionState state);
    Optional<ExecutionState> findById(ExecutionId id);
}

// infrastructure/adapter/out/JpaExecutionRepository.java
@Repository
public class JpaExecutionRepository implements ExecutionRepository {
    private final ExecutionJpaRepository jpa;
    private final ExecutionStateMapper mapper;

    @Override
    public void save(ExecutionState state) {
        jpa.save(mapper.toEntity(state));
    }
}
```

---

## Règle des Mappers

Toujours un mapper explicite entre domain record et JPA entity.
Ne jamais annoter un domain record avec `@Entity`.

```java
@Component
public class ExecutionStateMapper {
    public ExecutionStateEntity toEntity(ExecutionState domain) { ... }
    public ExecutionState toDomain(ExecutionStateEntity entity) { ... }
}
```

---

## Test de l'Architecture (ASM, pas ArchUnit)

ArchUnit 1.3/1.4 importe silencieusement 0 classe sur du bytecode Java 25 : tout test
`@AnalyzeClasses` est vacuous. Le projet vérifie donc les frontières avec un scan ASM.

```java
// platform-app/src/test/.../arch/ModuleDependencyTest.java
// - lit les .class des modules via l'ASM de Spring 7 (Java 25 compatible)
// - vérifie les arêtes interdites entre modules Maven
// - échoue si un module ne fournit aucune classe (anti-vacuité)
```

Règles couvertes : domaine sans Spring/JPA/Jackson, application sans infrastructure,
engine ↛ assertion/agent, reporting ↛ injection, assertion ↛ injection/infrastructure
(exception documentée pour `HttpMockAssertionExecutor`).

```bash
mvn -pl platform-app test -Dtest=ModuleDependencyTest
```

Pour le détail des frontières inter-modules, voir
`.claude/knowledge/skills/spring-modulith.md` (le nom du fichier est historique).

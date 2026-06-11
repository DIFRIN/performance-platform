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

## Test de l'Architecture avec ArchUnit

```java
@AnalyzeClasses(packages = "com.performance.platform")
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainHasNoDependencyOnSpring =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..");

    @ArchTest
    static final ArchRule applicationHasNoDependencyOnInfrastructure =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");
}
```

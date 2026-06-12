# ISSUE-039 — TaskExecutorRegistry (résolution par String taskName)

**PDR** : PDR-010
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-011
**Estime** : M

---

## Objectif

Créer le module `platform-infrastructure` (package `.executor`) et le `TaskExecutorRegistry`
qui collecte tous les `TaskExecutor` Spring et les résout par `String taskName`.

## Fichiers à Créer

```
platform-infrastructure/pom.xml — dépend de domain, application, plugin-api
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/
  ├── TaskExecutorRegistry.java
  ├── DefaultTaskExecutorRegistry.java
  └── UnsupportedTaskTypeException.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/
  └── DefaultTaskExecutorRegistryTest.java
```

## Interfaces à Implémenter

```java
public interface TaskExecutorRegistry {
    void register(TaskExecutor executor);
    TaskExecutor getFor(String taskName) throws UnsupportedTaskTypeException;
    Set<String> getSupportedTaskNames();
}

@Component
public class DefaultTaskExecutorRegistry implements TaskExecutorRegistry {
    public DefaultTaskExecutorRegistry(List<TaskExecutor> executors) { executors.forEach(this::register); }
}

public class UnsupportedTaskTypeException extends RuntimeException {
    public UnsupportedTaskTypeException(String taskName) { super("No TaskExecutor registered for taskName: " + taskName); }
}
```

## Règles Spécifiques

- Clé de registre = `executor.getSupportedTaskName()` (String). JAMAIS de switch/if sur un type.
- `getFor` sur un name inconnu → `UnsupportedTaskTypeException`.
- Tout le code dans `com.performance.platform.infrastructure.executor` (séparation stricte).

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] `getFor("database")` retourne le bon executor ; `getFor("xxx")` lève l'exception
- [ ] Aucun `if/switch` sur un type
- [ ] `progress.md` mis à jour : ISSUE-039 → DONE
- [ ] `context/interfaces-registry.md` : `TaskExecutorRegistry` → STABLE

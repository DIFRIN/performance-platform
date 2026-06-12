# ISSUE-047 — PluginRegistry (fusion interne/externe, lookup phase+name)

**PDR** : PDR-011
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-046
**Estime** : M

---

## Objectif

Implémenter `PluginRegistry` : fusionne les executors internes (Spring) et externes (plugins),
résout par `(Phase, name)`, gère les collisions (externe gagne, warning).

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/plugin/
  ├── PluginRegistry.java
  └── DefaultPluginRegistry.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/plugin/
  └── DefaultPluginRegistryTest.java — lookup, collision externe gagne
```

## Interfaces à Implémenter

```java
public interface PluginRegistry {
    TaskExecutor lookup(Phase phase, String name);
    boolean contains(Phase phase, String name);
    Set<String> namesFor(Phase phase);
}

@Component
public class DefaultPluginRegistry implements PluginRegistry {
    public DefaultPluginRegistry(List<TaskExecutor> internalExecutors, PluginLoader loader) { /* ... */ }
}
```

## Règles Spécifiques

- Phase déduite de l'annotation : `@Preparation`→PREPARATION, `@Injection`→INJECTION, `@Assertion`→ASSERTION.
- Collision même `(phase, name)` → plugin externe écrase l'interne, `PluginWarning` loggé.
- `lookup` sur `(phase, name)` inconnu → `UnsupportedTaskTypeException`.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] lookup par phase+name retourne le bon executor
- [ ] Collision : l'externe écrase l'interne + warning
- [ ] `progress.md` mis à jour : ISSUE-047 → DONE
- [ ] `context/interfaces-registry.md` : `PluginRegistry` → STABLE

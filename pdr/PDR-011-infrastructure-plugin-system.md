# PDR-011 — Plugin System (Infrastructure)

**Module Maven** : `platform-infrastructure`
**Package** : `com.performance.platform.infrastructure.plugin`
**Statut** : WAITING
**Specs de référence** : `specifications/03-task-framework.md` §7 (complet), `constraints.md` CF-04, CF-06, CF-07, ADR-007
**Dépend de** : PDR-001, PDR-003, PDR-010
**Issues** : ISSUE-046, ISSUE-047, ISSUE-048, ISSUE-049

---

## Responsabilité

Charge les JARs externes du répertoire `platform.plugins.dir` au démarrage (une seule fois,
pas de hot-reload), scanne les annotations `@Preparation/@Injection/@Assertion`, instancie
les `TaskExecutor`, et les fusionne avec les executors internes dans un `PluginRegistry`
unifié (lookup par phase + name). Gère les collisions (externe gagne, warning) et les erreurs
(JAR corrompu ignoré, pas de crash).

**Séparation stricte** : tout dans `com.performance.platform.infrastructure.plugin/`.

---

## Interfaces Publiques

```java
public interface PluginLoader {
    PluginLoadResult load(Path pluginDirectory);
}

public record PluginLoadResult(
    int jarsLoaded,
    int executorsRegistered,
    List<PluginWarning> warnings,
    List<PluginError> errors
) {
    public boolean hasErrors() { return !errors.isEmpty(); }
}

public record PluginWarning(String pluginJar, String message) {}
public record PluginError(String pluginJar, String message, Throwable cause) {}

public interface PluginRegistry {
    /** Résout par phase + name. Externe prime sur interne en cas de collision. */
    TaskExecutor lookup(Phase phase, String name);
    boolean contains(Phase phase, String name);
    Set<String> namesFor(Phase phase);
}

@Component
public class DefaultPluginLoader implements PluginLoader { /* URLClassLoader + scan annotations */ }

@Component
public class DefaultPluginRegistry implements PluginRegistry {
    public DefaultPluginRegistry(List<TaskExecutor> internalExecutors, PluginLoader loader) { /* ... */ }
}
```

---

## Règles de Comportement

- Chargement au démarrage uniquement (CF-06). Pas de rechargement à chaud.
- JAR invalide/corrompu → `PluginError` loggé WARN, JAR ignoré (pas de crash, CF-06).
- Classe non instanciable (pas de constructeur no-arg) → `PluginError`, classe ignorée.
- Collision de `name` même phase → plugin externe écrase l'interne, `PluginWarning` loggé.
- Phase déduite de l'annotation : `@Preparation`→PREPARATION, `@Injection`→INJECTION, `@Assertion`→ASSERTION.
- Aucun plugin trouvé → démarrage normal, log INFO.
- `platform.plugins.enabled=false` → loader court-circuité, registre = executors internes seuls.
- Le `taskName` du DSL = clé de lookup ; mapping `(phase, name) → TaskExecutor`.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → Phase
  PDR-003 → TaskExecutor, @Preparation/@Injection/@Assertion
  PDR-010 → executors internes (List<TaskExecutor>)

Ce PDR est utilisé par :
  PDR-009 (agent runtime) → résolution finale d'un executor par name
  PDR-018 (platform-app)  → bootstrap du PluginLoader au démarrage
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Test : JAR plugin valide chargé + collision externe gagne + JAR corrompu ignoré
- [ ] ArchUnit : code dans `.plugin` uniquement
- [ ] `PluginLoader`, `PluginRegistry` dans `context/interfaces-registry.md` STABLE

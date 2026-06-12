# ISSUE-046 — PluginLoader (chargement JARs au démarrage)

**PDR** : PDR-011
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-011, ISSUE-039
**Estime** : L

---

## Objectif

Implémenter `PluginLoader` (package `.plugin`) : scanne `platform.plugins.dir`, charge les
JARs via URLClassLoader, scanne les annotations, instancie les `TaskExecutor`. Tolère les
erreurs (JAR corrompu ignoré, pas de crash).

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/plugin/
  ├── PluginLoader.java
  ├── DefaultPluginLoader.java
  ├── PluginLoadResult.java
  ├── PluginWarning.java
  └── PluginError.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/plugin/
  └── DefaultPluginLoaderTest.java — JAR valide chargé, JAR corrompu ignoré, classe non-instanciable
```

## Interfaces à Implémenter

```java
public interface PluginLoader { PluginLoadResult load(Path pluginDirectory); }

public record PluginLoadResult(int jarsLoaded, int executorsRegistered,
    List<PluginWarning> warnings, List<PluginError> errors) {
    public boolean hasErrors() { return !errors.isEmpty(); }
}
public record PluginWarning(String pluginJar, String message) {}
public record PluginError(String pluginJar, String message, Throwable cause) {}
```

## Règles Spécifiques

- Chargement au démarrage uniquement (CF-06). Pas de hot-reload.
- JAR corrompu → `PluginError` loggé WARN, JAR ignoré (pas de crash).
- Classe sans constructeur no-arg → `PluginError`, classe ignorée.
- Aucun plugin trouvé → log INFO, démarrage normal.
- `platform.plugins.enabled=false` → loader court-circuité.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] JAR plugin valide → executor chargé
- [ ] JAR corrompu → `PluginError`, pas d'exception propagée
- [ ] `progress.md` mis à jour : ISSUE-046 → DONE
- [ ] `context/interfaces-registry.md` : `PluginLoader`, `PluginLoadResult` → STABLE

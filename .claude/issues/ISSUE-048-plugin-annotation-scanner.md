# ISSUE-048 — Scanner d'annotations plugin (réflexion)

**PDR** : PDR-011
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-046
**Estime** : M

---

## Objectif

Implémenter le scanner d'annotations qui, pour une classe `TaskExecutor` chargée, détecte
l'annotation `@Preparation/@Injection/@Assertion`, en extrait `name`/`version`/`description`
et la `Phase` correspondante.

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/plugin/
  ├── AnnotationScanner.java
  └── PluginDescriptor.java   — (name, version, description, phase, executorClass)

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/plugin/
  └── AnnotationScannerTest.java
```

## Interfaces à Implémenter

```java
public interface AnnotationScanner {
    Optional<PluginDescriptor> scan(Class<?> candidate);
}

public record PluginDescriptor(String name, String version, String description,
                               Phase phase, Class<? extends TaskExecutor> executorClass) {}
```

## Règles Spécifiques

- Exactement une des 3 annotations attendue (CF-07). Zéro ou plusieurs → `Optional.empty()` + warning.
- Mapping annotation → Phase comme dans ISSUE-047.
- Réflexion uniquement — pas de Spring requis pour le scan.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Classe annotée `@Injection(name="x")` → `PluginDescriptor(phase=INJECTION, name="x")`
- [ ] Classe sans annotation → `Optional.empty()`
- [ ] `.claude/progress.md` mis à jour : ISSUE-048 → DONE
- [ ] `.claude/context/interfaces-registry.md` mis à jour

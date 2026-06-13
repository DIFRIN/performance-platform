# ISSUE-059 — AssertionExecutorRegistry

**PDR** : PDR-014
**Module** : `platform-assertion`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-011
**Estime** : S

---

## Objectif

Créer le module `platform-assertion` et le `AssertionExecutorRegistry` qui collecte les
`AssertionExecutor` Spring et les résout par `String assertionName`.

## Fichiers à Créer

```
platform-assertion/pom.xml — dépend de domain, plugin-api, injection-gatling
platform-assertion/src/main/java/com/performance/platform/assertion/
  ├── AssertionExecutorRegistry.java
  └── DefaultAssertionExecutorRegistry.java

platform-assertion/src/test/java/com/performance/platform/assertion/
  └── DefaultAssertionExecutorRegistryTest.java
```

## Interfaces à Implémenter

```java
public interface AssertionExecutorRegistry {
    AssertionExecutor getFor(String assertionName);
    void register(AssertionExecutor executor);
}

@Component
public class DefaultAssertionExecutorRegistry implements AssertionExecutorRegistry {
    public DefaultAssertionExecutorRegistry(List<AssertionExecutor> executors) { /* ... */ }
}
```

## Règles Spécifiques

- Clé = `executor.getSupportedAssertionName()` (String). Pas de switch sur un type.
- `getFor` inconnu → exception claire.

## Critères de Done

- [ ] `mvn test -pl platform-assertion -q` → 0 erreur
- [ ] `getFor("gatling-metric")` retourne le bon executor
- [ ] `.claude/progress.md` mis à jour : ISSUE-059 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `AssertionExecutorRegistry` → STABLE

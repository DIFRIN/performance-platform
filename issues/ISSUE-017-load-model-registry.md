# ISSUE-017 — LoadModelRegistry

**PDR** : PDR-005
**Module** : `platform-scenario-dsl`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-015
**Estime** : S

---

## Objectif

Implémenter le registre des load models réutilisables.

## Fichiers à Créer

```
platform-scenario-dsl/src/main/java/com/performance/platform/scenario/loadmodel/
  ├── LoadModelRegistry.java          — interface
  ├── DefaultLoadModelRegistry.java   — implémentation (Map en mémoire)
  └── LoadModelNotFoundException.java

platform-scenario-dsl/src/test/java/com/performance/platform/scenario/loadmodel/
  └── DefaultLoadModelRegistryTest.java
```

## Interfaces à Implémenter

```java
public interface LoadModelRegistry {
    void register(String name, LoadModel model);
    LoadModel get(String name) throws LoadModelNotFoundException;
    Map<String, LoadModel> getAll();
}
public class LoadModelNotFoundException extends RuntimeException {
    public LoadModelNotFoundException(String name) { super("LoadModel not found: " + name); }
}
```

## Règles Spécifiques

- `getAll()` retourne une copie immuable.
- `get()` sur un nom absent → `LoadModelNotFoundException`.

## Critères de Done

- [ ] `mvn test -pl platform-scenario-dsl -q` → 0 erreur
- [ ] `get("absent")` lève `LoadModelNotFoundException`
- [ ] `progress.md` mis à jour : ISSUE-017 → DONE
- [ ] `context/interfaces-registry.md` : `LoadModelRegistry` → STABLE

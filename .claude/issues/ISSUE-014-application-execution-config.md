# ISSUE-014 — ExecutionConfig (record de configuration applicative)

**PDR** : PDR-004
**Module** : `platform-application`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-012
**Estime** : S

---

## Objectif

Créer le record `ExecutionConfig` regroupant les timeouts et la politique de complétion.

## Fichiers à Créer

```
platform-application/src/main/java/com/performance/platform/application/config/
  └── ExecutionConfig.java

platform-application/src/test/java/com/performance/platform/application/config/
  └── ExecutionConfigTest.java — instanciation
```

## Interfaces à Implémenter

```java
public record ExecutionConfig(
    Duration taskAvailabilityTimeout,     // >= 120s en K8s (NOTE R5)
    Duration taskExecutionTimeout,
    Duration workInProgressResetInterval, // <= taskExecutionTimeout / 3 (NOTE R6)
    TaskCompletionPolicy completionPolicy
) {}
```

## Règles Spécifiques

- `TaskCompletionPolicy` vient de `platform-domain`.
- Pas de validation des contraintes R5/R6 ici (informatif) — c'est l'engine qui les applique.

## Critères de Done

- [ ] `mvn test -pl platform-application -q` → 0 erreur
- [ ] `.claude/progress.md` mis à jour : ISSUE-014 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `ExecutionConfig` → STABLE

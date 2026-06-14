# ISSUE-016 — ScenarioValidator (erreurs + warnings + détection de cycle DAG)

**PDR** : PDR-005
**Module** : `platform-scenario-dsl`
**Statut** : IN REVIEW
**Priorité** : P1
**Bloquée par** : ISSUE-015
**Estime** : L

---

## Objectif

Implémenter `ScenarioValidator` qui produit un `ValidationResult` (errors + warnings)
incluant la détection de cycle DAG et la cohérence des `requiredContexts`.

## Fichiers à Créer

```
platform-scenario-dsl/src/main/java/com/performance/platform/scenario/validation/
  ├── ScenarioValidator.java          — interface
  ├── DefaultScenarioValidator.java   — implémentation
  ├── ValidationResult.java
  ├── ValidationError.java
  ├── ValidationWarning.java
  └── DagCycleDetector.java           — tri topologique

platform-scenario-dsl/src/test/java/com/performance/platform/scenario/validation/
  ├── DefaultScenarioValidatorTest.java
  └── DagCycleDetectorTest.java
```

## Interfaces à Implémenter

```java
public interface ScenarioValidator { ValidationResult validate(ScenarioDefinition scenario); }
public record ValidationResult(boolean valid, List<ValidationError> errors, List<ValidationWarning> warnings) {}
public record ValidationError(String field, String message, String path) {}
public record ValidationWarning(String field, String message) {}
```

## Règles Spécifiques (erreurs bloquantes)

- `scenario.id` : obligatoire, alphanumérique + tirets, max 100 chars
- `scenario.version` : semver strict `x.y.z`
- `step.id` unique dans le scénario
- `dependsOn` : ids existants, pas de cycle (DAG)
- `requiredContexts` : chaque clé = id d'un step ANTÉRIEUR dans l'ordre topologique
- `loadModel` référencé par un step INJECTION `gatling` : doit exister dans `loadModels`
- `phase` obligatoire sur chaque step
- `task` obligatoire (taskName) — NON validé contre un registre

## Règles Spécifiques (warnings)

- pas de `metadata.owner` ; `timeout` absent sur step INJECTION ; aucun step ASSERTION ; `requiredContexts` vide sur step ASSERTION

## Critères de Done

- [ ] `mvn test -pl platform-scenario-dsl -q` → 0 erreur
- [ ] Un scénario avec cycle → `valid=false` avec erreur de cycle
- [ ] `requiredContext` référençant un step postérieur → erreur
- [ ] Le YAML de référence (ISSUE-015) → `valid=true`
- [ ] `.claude/progress.md` mis à jour : ISSUE-016 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `ScenarioValidator` → STABLE

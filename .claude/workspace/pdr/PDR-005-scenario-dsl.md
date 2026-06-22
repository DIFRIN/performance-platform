# PDR-005 — Scenario DSL (Parsing & Validation)

**Module Maven** : `platform-scenario-dsl`
**Package** : `com.performance.platform.scenario`
**Statut** : WAITING
**Specs de référence** : `.claude/knowledge/specs/01-scenario-dsl.md` (complet)
**Dépend de** : PDR-001, PDR-004
**Issues** : ISSUE-015, ISSUE-016, ISSUE-017, ISSUE-018

---

## Responsabilité

Parse le YAML de scénario en `ScenarioDefinition` immuable, valide la structure
(DAG sans cycle, ids uniques, requiredContexts cohérents, semver), et gère le registre
de load models. Sortie : un `ScenarioDefinition` valide ou des erreurs détaillées
(champ + message + path). Utilise SnakeYAML + Jackson.

---

## Interfaces Publiques

```java
public interface ScenarioParser {
    ScenarioDefinition parse(InputStream yamlContent) throws ScenarioParsingException;
    ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException;
    ScenarioDefinition parseFile(Path scenarioFile) throws ScenarioParsingException;
}

public interface ScenarioValidator {
    ValidationResult validate(ScenarioDefinition scenario);
}

public interface LoadModelRegistry {
    void register(String name, LoadModel model);
    LoadModel get(String name) throws LoadModelNotFoundException;
    Map<String, LoadModel> getAll();
}
```

### Records de validation et exceptions

```java
public record ValidationResult(
    boolean valid,
    List<ValidationError> errors,
    List<ValidationWarning> warnings
) {}

public record ValidationError(String field, String message, String path) {}
public record ValidationWarning(String field, String message) {}

public class ScenarioParsingException extends RuntimeException {
    private final List<String> errors;
    public ScenarioParsingException(String message, List<String> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }
    public List<String> getErrors() { return errors; }
}

public class ScenarioValidationException extends RuntimeException {
    private final ValidationResult result;
    public ScenarioValidationException(ValidationResult result) {
        super("Scenario validation failed");
        this.result = result;
    }
    public ValidationResult getResult() { return result; }
}

public class LoadModelNotFoundException extends RuntimeException {
    public LoadModelNotFoundException(String name) { super("LoadModel not found: " + name); }
}
```

---

## Règles de Comportement

### Erreurs bloquantes (validate → valid=false)
- `scenario.id` : obligatoire, alphanumérique + tirets, max 100 chars
- `scenario.version` : obligatoire, semver strict (`x.y.z`)
- chaque `step.id` unique dans le scénario
- `dependsOn` : référence des ids existants
- `dependsOn` : pas de cycle (DAG valide) — détecter via tri topologique
- `requiredContexts` : chaque clé = id d'un step antérieur dans l'ordre topologique
- `loadModel` référencé par un step INJECTION `gatling` : doit exister dans `loadModels`
- `phase` obligatoire sur chaque step
- `task` (taskName) obligatoire — PAS validé contre un registre (agents inconnus au parse)

### Avertissements (non bloquants)
- pas de `metadata.owner`
- `timeout` absent sur step INJECTION
- aucun step de phase ASSERTION
- `requiredContexts` vide sur un step ASSERTION

### Parsing
- Durations YAML (`30s`, `5m`, `2h`) → `java.time.Duration`.
- Un YAML invalide structurellement → `ScenarioParsingException` avec liste d'erreurs.
- Mapping vers les records du domaine (`StepDefinition`, `LoadModel`) — jamais d'exposition des DTO Jackson.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → ScenarioDefinition, StepDefinition, LoadModel, LoadModelType, Phase,
            TaskId, ScenarioId, ExecutionMode, RetryPolicy
  PDR-004 → ScenarioParsingUseCase (implémenté ici)

Ce PDR est utilisé par :
  PDR-006 (execution engine) → consomme ScenarioDefinition
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Le YAML de référence de la spec 01 parse et valide sans erreur
- [ ] Détection de cycle DAG testée
- [ ] Interfaces dans `.claude/context/interfaces-registry.md` STABLE

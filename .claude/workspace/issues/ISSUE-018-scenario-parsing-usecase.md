# ISSUE-018 — ScenarioParsingUseCase (assemblage parser + validator)

**PDR** : PDR-005
**Module** : `platform-scenario-dsl`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-015, ISSUE-016
**Estime** : S

---

## Objectif

Implémenter le use case `ScenarioParsingUseCase` (port de `platform-application`) en
combinant `ScenarioParser` + `ScenarioValidator`. Lève si invalide.

## Fichiers à Créer

```
platform-scenario-dsl/src/main/java/com/performance/platform/scenario/usecase/
  └── DefaultScenarioParsingService.java   — implements ScenarioParsingUseCase

platform-scenario-dsl/src/test/java/com/performance/platform/scenario/usecase/
  └── DefaultScenarioParsingServiceTest.java
```

## Interfaces à Implémenter

```java
@Service
public class DefaultScenarioParsingService implements ScenarioParsingUseCase {
    public DefaultScenarioParsingService(ScenarioParser parser, ScenarioValidator validator) { /* ... */ }
    public ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException {
        var scenario = parser.parse(yamlContent);
        var result = validator.validate(scenario);
        if (!result.valid()) throw new ScenarioValidationException(result);
        return scenario;
    }
}
```

## Règles Spécifiques

- `ScenarioValidationException` créée ici (module scenario-dsl) avec `getResult()`.
- Les warnings n'empêchent pas le parsing — seuls les errors bloquent.

## Critères de Done

- [ ] `mvn test -pl platform-scenario-dsl -q` → 0 erreur
- [ ] YAML valide → `ScenarioDefinition` retourné
- [ ] YAML avec erreur de validation → `ScenarioValidationException`
- [ ] `.claude/progress.md` mis à jour : ISSUE-018 → DONE
- [ ] `.claude/context/interfaces-registry.md` mis à jour

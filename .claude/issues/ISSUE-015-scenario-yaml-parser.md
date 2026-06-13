# ISSUE-015 — ScenarioParser (YAML → ScenarioDefinition)

**PDR** : PDR-005
**Module** : `platform-scenario-dsl`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-003, ISSUE-012
**Estime** : L

---

## Objectif

Créer le module `platform-scenario-dsl` et implémenter `ScenarioParser` qui transforme un
YAML de scénario en `ScenarioDefinition` immuable (SnakeYAML + Jackson). Mapper les durations
(`30s`, `5m`, `2h`) en `Duration`.

## Fichiers à Créer

```
platform-scenario-dsl/pom.xml — dépend de platform-domain, platform-application, snakeyaml, jackson
platform-scenario-dsl/src/main/java/com/performance/platform/scenario/parser/
  ├── ScenarioParser.java          — interface
  ├── YamlScenarioParser.java      — implémentation
  ├── DurationParser.java          — "30s"/"5m"/"2h" → Duration
  └── dto/                         — DTO Jackson internes (non exposés)

platform-scenario-dsl/src/test/java/com/performance/platform/scenario/parser/
  ├── YamlScenarioParserTest.java  — parse le YAML de référence
  └── DurationParserTest.java
```

## Interfaces à Implémenter

```java
public interface ScenarioParser {
    ScenarioDefinition parse(InputStream yamlContent) throws ScenarioParsingException;
    ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException;
    ScenarioDefinition parseFile(Path scenarioFile) throws ScenarioParsingException;
}
```

## YAML de Référence (doit parser sans erreur)

```yaml
scenario:
  id: customer-api-perf
  name: Customer API Campaign
  version: 1.0.0
  tags: [performance, regression]
  metadata: { owner: team-a, jira: PERF-123 }
  execution: { mode: DISTRIBUTED }
  taskAvailabilityTimeoutSeconds: 120
  steps:
    - id: purge-db
      task: database
      phase: PREPARATION
      requiredContexts: []
      dependsOn: []
      parameters: { operation: PURGE, datasource: customer-db }
      timeout: 30s
    - id: customer-api-load
      task: gatling
      phase: INJECTION
      requiredContexts: [purge-db]
      dependsOn: [purge-db]
      parameters: { simulation: com.example.CustomerApiSimulation, loadModel: api-load }
      timeout: 20m
loadModels:
  api-load:
    type: RAMP
    stages:
      - { duration: 2m, usersPerSecond: 10 }
      - { duration: 5m, usersPerSecond: 100 }
```

## Règles Spécifiques

- `step.task` (YAML) → `StepDefinition.taskName` (String).
- `LoadModel.parameters` contient les champs spécifiques au type (`stages`, `usersPerSecond`...).
- Les DTO Jackson restent internes ; on retourne uniquement des records domaine.
- YAML malformé → `ScenarioParsingException` (de `platform-application`) avec liste d'erreurs.
- `ScenarioParsingException` réutilisée depuis `platform-application` (ne pas redéfinir).

## Critères de Done

- [ ] `mvn test -pl platform-scenario-dsl -q` → 0 erreur
- [ ] Le YAML de référence parse en `ScenarioDefinition` complet
- [ ] `30s`/`5m`/`2h` → `Duration` corrects
- [ ] `.claude/progress.md` mis à jour : ISSUE-015 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `ScenarioParser` → STABLE

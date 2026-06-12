# PDR-013 — Gatling Injection

**Module Maven** : `platform-injection-gatling`
**Package** : `com.performance.platform.injection.gatling`
**Statut** : WAITING
**Specs de référence** : `specifications/06-injection-gatling.md` (complet), `specifications/01-scenario-dsl.md` §3
**Dépend de** : PDR-001, PDR-003
**Issues** : ISSUE-054, ISSUE-055, ISSUE-056, ISSUE-057, ISSUE-058

---

## Responsabilité

Exécute des simulations Gatling (Java DSL uniquement) in-process, traduit les `LoadModel`
en `OpenInjectionStep`, lance via `GatlingRunner`, parse les résultats (`stats.json`) en
`InjectionResult`, et expose le tout via `GatlingTaskExecutor` (`@Injection(name="gatling")`).

---

## Interfaces Publiques

```java
@Injection(name = "gatling", description = "Gatling load injection")
public class GatlingTaskExecutor implements TaskExecutor {
    public String getSupportedTaskName() { return "gatling"; }
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        // retourne TaskResult.success(step.id(), "gatling", elapsed, Map.of("result", injectionResult))
    }
}

public interface GatlingRunner {
    Path run(GatlingRunConfig config) throws GatlingExecutionException;
}

public record GatlingRunConfig(
    String simulationClass,
    LoadModel loadModel,
    Map<String, String> systemProperties,
    Path resultsDirectory,
    String simulationId,
    Duration timeout
) {}

public interface LoadModelTranslator {
    List<OpenInjectionStep> translate(LoadModel model);   // OpenInjectionStep = type Gatling
}

public interface GatlingResultParser {
    InjectionResult parse(Path gatlingResultDirectory, TaskId taskId) throws ResultParsingException;
}

public class GatlingExecutionException extends RuntimeException {
    public GatlingExecutionException(String message, Throwable cause) { super(message, cause); }
}
public class ResultParsingException extends RuntimeException {
    public ResultParsingException(String message, Throwable cause) { super(message, cause); }
}
```

---

## Règles de Comportement

- Java DSL Gatling uniquement (CD-03). Pas de Scala.
- `getSupportedTaskName()` retourne `"gatling"` (String) ; `TaskResult` utilise `String taskName`.
- `LoadModelTranslator` couvre les 8 types : RAMP, RAMP_UP_DOWN, CONSTANT, SPIKE, STAIR, SOAK, BURST, CUSTOM.
  - RAMP → `rampUsersPerSec(from).to(to).during(d)` par stage
  - CONSTANT → `constantUsersPerSec(n).during(d)`
  - SPIKE → ramp base→spike sur spikeDuration
  - STAIR → escalier par step
  - SOAK → constant + ramp initial
  - BURST → `nothingFor()` + `atOnceUsers()` répété
  - RAMP_UP_DOWN → rampUp + hold + rampDown
  - CUSTOM → interpolation linéaire entre points
- `GatlingResultParser` lit `stats.json` → métriques globales (p50/p75/p90/p95/p99, errorRate, throughput).
- `InjectionResult` stocké sous clé `"result"` dans `TaskResult.outputs`.
- `errorRate` en pourcentage (0.0–100.0).
- `onScenarioRestart` : arrêter la simulation en cours, fermer connexions Gatling.
- Échec de simulation → `TaskResult.failed()`, jamais d'exception propagée pour échec métier.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → InjectionResult, LoadModel, LoadModelType, TaskId, TaskResult,
            ExecutionContext, StepDefinition
  PDR-003 → TaskExecutor, @Injection

Ce PDR est utilisé par :
  PDR-009 (agent runtime) → enregistré dans TaskExecutorRegistry
  PDR-014 (assertion)     → lit InjectionResult depuis le contexte
  PDR-015 (reporting)     → InjectionReportEntry
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Test : chaque LoadModelType produit le bon OpenInjectionStep
- [ ] Test : parsing d'un stats.json de référence → InjectionResult correct
- [ ] Interfaces dans `context/interfaces-registry.md` STABLE

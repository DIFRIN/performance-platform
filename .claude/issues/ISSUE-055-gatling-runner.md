# ISSUE-055 — GatlingRunner (lancement in-process)

**PDR** : PDR-013
**Module** : `platform-injection-gatling`
**Statut** : IN REVIEW
**Priorité** : P1
**Bloquée par** : ISSUE-054
**Estime** : M

---

## Objectif

Implémenter `GatlingRunner` qui lance une simulation Gatling in-process et retourne le
répertoire de résultats.

## Fichiers à Créer

```
platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/runner/
  ├── GatlingRunner.java
  ├── DefaultGatlingRunner.java
  ├── GatlingRunConfig.java
  └── GatlingExecutionException.java

platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/runner/
  └── DefaultGatlingRunnerTest.java — simulation minimale → répertoire produit
```

## Interfaces à Implémenter

```java
public interface GatlingRunner { Path run(GatlingRunConfig config) throws GatlingExecutionException; }

public record GatlingRunConfig(String simulationClass, LoadModel loadModel,
    Map<String, String> systemProperties, Path resultsDirectory, String simulationId, Duration timeout) {}

public class GatlingExecutionException extends RuntimeException {
    public GatlingExecutionException(String message, Throwable cause) { super(message, cause); }
}
```

## Règles Spécifiques

- Java DSL uniquement (CD-03). Pas de Scala.
- Le `LoadModelTranslator` injecte les `OpenInjectionStep` dans la simulation.
- Respecter `timeout` ; lancement sous Virtual Threads.
- Retourne le `Path` du répertoire de résultats Gatling.

## Critères de Done

- [ ] `mvn test -pl platform-injection-gatling -q` → 0 erreur
- [ ] Une simulation HTTP minimale s'exécute et produit un répertoire de résultats
- [ ] `.claude/progress.md` mis à jour : ISSUE-055 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `GatlingRunner` → STABLE

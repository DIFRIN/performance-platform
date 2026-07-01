# PDR-037 — Engine Unified Assertion Dispatch & Lifecycle Signals

**Module Maven** : `platform-execution-engine`, `platform-agent-runtime`
**Package** : `com.performance.platform.engine`, `com.performance.platform.agent.runtime`
**Status** : WAITING
**Specs de reference** : `.claude/knowledge/specs/02-execution-engine.md`, `.claude/workspace/assertion-distributed-analysis.md` sections 4-5
**Depend de** : PDR-035 (AssertionExecutor extends TaskExecutor), PDR-036 (AssertionResultMapper), PDR-038 (ExecutionLifecycleSignal)
**Issues** : ISSUE-154, ISSUE-155, ISSUE-156, ISSUE-158, ISSUE-159

---

## Responsabilite

Unifie le dispatch des assertions dans l'engine d'execution et introduit le mecanisme de signaux de cycle de vie (`ExecutionLifecycleSignal`) pour coordonner les assertions basees sur intervalles. Avec `AssertionExecutor extends TaskExecutor` (PDR-035), les assertions sont desormais resolvables via le meme `TaskExecutorLookup.findTaskExecutor()` que les taches de preparation et d'injection. Ce PDR :

1. Simplifie `DagPhaseExecutor` : supprime le chemin special `executeAssertionStep()` ; les assertions passent par le chemin unifie
2. Simplifie `TaskExecutorLookup` : retire `findAssertionExecutor()` (ou le deprecie)
3. Ajoute le dispatch de `ExecutionLifecycleSignal` (START/STOP) autour de l'execution des taches
4. Coordonne les assertions avec `linkedTo` : START avant l'injection liee, STOP apres
5. Gere les comportements d'arret : `immediate`, `completeCurrentCycle`, `gracePeriod`
6. Permet au `RemoteExecutionEngine` de dispatcher les assertions vers les agents

---

## Interfaces Publiques

### LifecycleAction enum (PDR-038, rappel)

```java
// Dans platform-domain, PDR-038 :
public enum LifecycleAction {
    START,    // Begin execution/monitoring
    STOP      // Terminate execution/monitoring and produce final result
}
```

### ExecutionLifecycleSignal record (PDR-038, rappel)

```java
// Dans platform-domain, PDR-038 :
public record ExecutionLifecycleSignal(
    SignalId id,
    ExecutionId executionId,
    TaskId taskId,
    LifecycleAction action,           // START or STOP
    Map<String, Object> parameters,   // stopBehavior, gracePeriodDuration, etc.
    Instant issuedAt
) implements AgentSignal {}
```

### AgentSignal sealed hierarchy (PDR-038, rappel)

```java
// Dans platform-domain, PDR-038 -- mis a jour :
public sealed interface AgentSignal
        permits ScenarioRestartSignal, ExecutionLifecycleSignal {
    SignalId id();
    Instant issuedAt();
}
```

### StepDefinition (inchange, mais note YAML)

Nouveaux parametres YAML autorises dans `parameters` d'un step ASSERTION avec `linkedTo` :

```yaml
# Dans scenario.yaml, step ASSERTION avec interval:
- id: wiremock-assertion
  task: wiremock
  phase: ASSERTION
  linkedTo: customer-api-load          # injection a surveiller
  dependsOn: []
  requiredContexts:
    - customer-api-load
  parameters:
    operator: GTE
    value: 1000
    intervalSeconds: 5                # frequence de sampling
    stopBehavior: completeCurrentCycle # immediate | completeCurrentCycle | gracePeriod
    gracePeriodDuration: 10s          # uniquement si stopBehavior=gracePeriod
```

Ces parametres sont lus par l'engine depuis `StepDefinition.parameters()` et transmis dans `ExecutionLifecycleSignal.parameters()`. Ils NE sont PAS encodes dans des types du domaine.

---

## Flux Complet du Cycle de Vie

### 1. Assertion avec `linkedTo` (interval-based)

```
PHASE PREPARATION
  1. ExecutionPlanBuilder place les steps ASSERTION avec linkedTo
     dans assertionIntervalSteps (nouvelle liste dans ExecutionPlan, voir section ci-dessous)

  2. Engine execute PREPARATION (standard DAG, pas de changement)

  3. Avant INJECTION :
     a. Pour chaque assertionIntervalStep :
        - Engine envoie ExecutionLifecycleSignal(action=START, taskId=assertion.step.id())
          aux agents qui supportent le taskName de l'assertion
        - Signal.parameters contient : intervalSeconds, stopBehavior, gracePeriodDuration
     b. Agent recoit le signal START :
        - Resout l'AssertionExecutor pour ce taskName
        - Appelle setup() : configure le monitoring (ouvre connexion Kafka/HTTP, etc.)
        - Publie TaskClaimedByAgent (ou un event equivalent de demarrage)
        - Demarre une boucle de sampling a intervalSeconds
        - Publie TaskWorkInProgress a chaque echantillon

PHASE INJECTION (concurrente avec le monitoring)
  4. Engine execute INJECTION (standard DAG)
     Les agents d'assertion samplent independamment en arriere-plan

PHASE ASSERTION (apres INJECTION complete)
  5. Quand TOUTES les injections sont terminees :
     a. Engine envoie ExecutionLifecycleSignal(action=STOP, taskId=assertion.step.id())
        avec stopBehavior dans parameters
     b. Agent recoit le signal STOP :
        - Selon stopBehavior :
          * immediate : arrete la boucle de sampling immediatement,
            produit AssertionSummary a partir de l'historique existant
          * completeCurrentCycle : termine le cycle de sampling en cours si un est actif,
            puis arrete et produit AssertionSummary
          * gracePeriod : continue le sampling pendant gracePeriodDuration apres le signal STOP,
            puis arrete et produit AssertionSummary
        - Appelle teardown() : nettoie les ressources (ferme connexions)
        - Construit AssertionSummary avec l'historique complet
        - Publie TaskCompleted avec TaskResult contenant AssertionSummary dans outputs["assertion"]

  6. Engine collecte les TaskCompleted pour chaque assertion
  7. Pour les assertions POINT-IN-TIME (sans linkedTo) :
     - Execution standard : execute() une fois dans ASSERTION phase (comme avant)
```

### 2. Assertion sans `linkedTo` (point-in-time, comportement existant)

```
  1. Engine execute PREPARATION (standard)
  2. Engine execute INJECTION (standard)
  3. Engine execute ASSERTION phase :
     a. Pour chaque step ASSERTION sans linkedTo :
        - Envoie ExecutionLifecycleSignal(START) (informatif, meme pour point-in-time)
        - Execute la task normalement via findTaskExecutor(step.taskName()).execute(context, step)
        - Envoie ExecutionLifecycleSignal(STOP)
        - L'executor s'execute une fois et retourne TaskResult
```

### 3. Tasks non-assertion (preparation, injection)

```
  Toutes les tasks recoivent START/STOP egalement :
    1. Engine envoie ExecutionLifecycleSignal(START) avant d'executer la task
    2. Engine execute la task normalement
    3. Engine envoie ExecutionLifecycleSignal(STOP) apres completion
  Cela permet aux agents de suivre l'etat de n'importe quelle task, pas seulement les assertions.
```

---

## ExecutionPlan (extension)

```java
// Dans platform-domain, ExecutionPlan -- ajout d'un champ :
public record ExecutionPlan(
    ExecutionId id,
    ScenarioId scenarioId,
    List<ExecutionStep> preparationSteps,          // inchange
    List<ExecutionStep> injectionSteps,            // inchange
    List<ExecutionStep> assertionSteps,             // inchange -- assertions point-in-time
    List<ExecutionStep> assertionIntervalSteps,    // NOUVEAU -- assertions avec linkedTo
    ExecutionContext initialContext
) {}
```

`assertionIntervalSteps` est peuple par `ExecutionPlanBuilder` pour les steps ASSERTION qui ont `StepDefinition.parameters().get("linkedTo") != null`.

---

## ExecutionPlanBuilder (modification)

Le builder doit :
1. Lire `linkedTo` depuis `StepDefinition.parameters()` pour les steps de phase ASSERTION
2. Si `linkedTo` est present, placer le step dans `assertionIntervalSteps` (pas dans `assertionSteps`)
3. Valider que `linkedTo` reference un step de phase INJECTION existant dans le scenario
4. Les `dependsOn` d'un step d'assertion avec `linkedTo` sont evalues comme d'habitude -- mais le step d'assertion n'attend PAS la completion de l'injection pour START (il attend le signal)

---

## RemoteExecutionEngine (modifications)

```java
// Dans RemoteExecutionEngine, nouvelle methode :
private void dispatchLifecycleSignal(
    ExecutionId executionId,
    TaskId taskId,
    LifecycleAction action,
    Map<String, Object> parameters
) {
    var signal = new ExecutionLifecycleSignal(
        SignalId.generate(),
        executionId,
        taskId,
        action,
        parameters,
        Instant.now()
    );
    transport.broadcastSignal(signal);
}

// Sequence d'execution modifiee :
// ...
// 2. executePhase(PREPARATION)
// 3. Pour chaque assertionIntervalStep :
//      dispatchLifecycleSignal(execId, step.id(), LifecycleAction.START, step.parameters())
// 4. executePhase(INJECTION) -- les agents d'assertion samplent en parallele
// 5. Pour chaque assertionIntervalStep :
//      dispatchLifecycleSignal(execId, step.id(), LifecycleAction.STOP, step.parameters())
// 6. Pour chaque assertionIntervalStep :
//      Attendre TaskCompleted (avec timeout / completionPolicy)
// 7. executePhase(ASSERTION) -- assertions point-in-time (pas de linkedTo)
```

**Note importante** : `transport.broadcastSignal()` existe deja dans l'interface `ExecutionTransport`. Les implementations (Kafka, RabbitMQ, HTTP, Socket, InMemory) supportent deja `AgentSignal` et `ScenarioRestartSignal`. L'ajout de `ExecutionLifecycleSignal` dans la hierarchie `sealed` de `AgentSignal` le rend automatiquement transportable par tous les transports -- aucun changement transport necessaire.

---

## LocalExecutionEngine (modifications)

Meme logique que `RemoteExecutionEngine` mais sans transport :
- `LocalAgent` recoit directement le signal (appel de methode)
- Le sampling et l'execution se font dans la meme JVM via Virtual Threads

```java
// Dans LocalExecutionEngine :
// ...
// 2. executePhase(PREPARATION)
// 3. Pour chaque assertionIntervalStep :
//      localAgent.onLifecycleSignal(new ExecutionLifecycleSignal(...START...))
// 4. executePhase(INJECTION)
// 5. Pour chaque assertionIntervalStep :
//      localAgent.onLifecycleSignal(new ExecutionLifecycleSignal(...STOP...))
// 6. Attendre completion des assertions d'intervalle
// 7. executePhase(ASSERTION)
```

---

## DagPhaseExecutor -- simplification (ISSUE-154)

Le `DagPhaseExecutor` subit deux changements :

1. Le `executeSingleStep()` n'a plus de branche conditionnelle sur `phase == Phase.ASSERTION`
2. `assertionResultToTaskResult()` est remplace par l'appel a `AssertionResultMapper.toTaskResult()` (PDR-036)

```java
// Dans DagPhaseExecutor.executeSingleStep() -- AVANT (a supprimer) :
//            if (phase == Phase.ASSERTION) {
//                result = executeAssertionStep(stepDef, context, lookup, policy);
//            } else {
//                result = executePreparationOrInjectionStep(stepDef, context, lookup, policy);
//            }

// Dans DagPhaseExecutor.executeSingleStep() -- APRES :
    private StepExecutionResult executeSingleStep(
            ExecutionStep execStep,
            ExecutionContext context,
            Phase phase,
            TaskExecutorLookup lookup,
            ExecutionLifecycleDispatcher lifecycleDispatcher) {

        StepDefinition stepDef = execStep.step();
        RetryPolicy policy = stepDef.retryPolicy() != null
                ? stepDef.retryPolicy()
                : RetryPolicy.defaults();

        var start = Instant.now();

        try {
            // Envoyer START signal
            lifecycleDispatcher.dispatch(stepDef.id(), LifecycleAction.START);

            // Unified path: assertions are TaskExecutor too (PDR-035)
            TaskResult result = executeStep(stepDef, context, lookup, policy);
            var duration = Duration.between(start, Instant.now());

            // Envoyer STOP signal
            lifecycleDispatcher.dispatch(stepDef.id(), LifecycleAction.STOP);

            log.info("action=step_completed taskId={} taskName={} status={} durationMs={}",
                    stepDef.id().value(), stepDef.taskName(), result.status(), duration.toMillis());
            return new StepExecutionResult(stepDef.id(), result);
        } catch (Exception e) {
            var duration = Duration.between(start, Instant.now());
            lifecycleDispatcher.dispatch(stepDef.id(), LifecycleAction.STOP);
            var failedResult = TaskResult.failed(
                    stepDef.id(), stepDef.taskName(), duration, e.getMessage(), e);
            log.warn("action=step_exhausted taskId={} taskName={} durationMs={} error={}",
                    stepDef.id().value(), stepDef.taskName(), duration.toMillis(), e.getMessage());
            return new StepExecutionResult(stepDef.id(), failedResult);
        }
    }

    private TaskResult executeStep(
            StepDefinition stepDef,
            ExecutionContext context,
            TaskExecutorLookup lookup,
            RetryPolicy policy) {

        TaskExecutor executor = lookup.findTaskExecutor(stepDef.taskName());
        if (executor == null) {
            return TaskResult.failed(stepDef.id(), stepDef.taskName(),
                    Duration.ZERO, "No TaskExecutor found for taskName: " + stepDef.taskName(), null);
        }

        return retryExecutor.executeWithRetry(policy, () -> executor.execute(context, stepDef));
    }
```

### ExecutionLifecycleDispatcher (nouvelle interface interne)

```java
package com.performance.platform.engine.local;

import com.performance.platform.domain.id.TaskId;

/**
 * Dispatche les signaux de cycle de vie START/STOP.
 * <p>
 * En LOCAL : appelle LocalAgent.onLifecycleSignal().
 * En DISTRIBUTED : appelle transport.broadcastSignal().
 */
@FunctionalInterface
public interface ExecutionLifecycleDispatcher {
    void dispatch(TaskId taskId, LifecycleAction action);
}
```

Deux implementations :
- `LocalLifecycleDispatcher` : injecte `LocalAgent` et appelle `localAgent.onLifecycleSignal(signal)`
- `RemoteLifecycleDispatcher` : injecte `ExecutionTransport` et appelle `transport.broadcastSignal(signal)`

---

## TaskExecutorLookup -- deprecation (ISSUE-155)

```java
package com.performance.platform.engine.local;

import com.performance.platform.plugin.TaskExecutor;

/**
 * Resout un nom de tache vers son executeur.
 * <p>
 * Depuis PDR-035 ({@code AssertionExecutor extends TaskExecutor}),
 * la recherche d'assertion est unifiee avec la recherche de tache.
 * {@link #findAssertionExecutor(String)} est deprecie.
 */
public interface TaskExecutorLookup {

    /**
     * Trouve le {@link TaskExecutor} pour un nom de tache donne.
     * Fonctionne pour PREPARATION, INJECTION, et ASSERTION (via AssertionExecutor extends TaskExecutor).
     *
     * @param taskName le nom de la tache
     * @return l'executeur, ou null si non trouve
     */
    TaskExecutor findTaskExecutor(String taskName);

    /**
     * @deprecated Depuis PDR-035, utiliser {@link #findTaskExecutor(String)}
     *             qui resout aussi les assertions.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    default com.performance.platform.plugin.AssertionExecutor findAssertionExecutor(String assertionName) {
        TaskExecutor executor = findTaskExecutor(assertionName);
        if (executor instanceof com.performance.platform.plugin.AssertionExecutor ae) {
            return ae;
        }
        return null;
    }
}
```

---

## AgentRuntime -- nouvelle methode onLifecycleSignal (ISSUE-159)

```java
// Dans platform-agent-runtime, AgentRuntime interface :
public interface AgentRuntime {
    // ... methodes existantes ...

    /**
     * Recoit un signal de cycle de vie pour une task.
     * START : preparer l'execution. Pour assertions interval : demarrer la boucle de sampling.
     * STOP  : terminer l'execution. Pour assertions interval : arreter le sampling, produire le resultat final.
     */
    void onLifecycleSignal(ExecutionLifecycleSignal signal);
}
```

Le `AgentSignalHandler` associe route deja les signaux -- le `ExecutionLifecycleSignal` arrive via `transport.receiveSignal()`.

---

## Stop Behaviors (implementation agent)

| stopBehavior | Comportement agent au signal STOP |
|---|---|
| `immediate` (default) | Arreter la boucle de sampling immediatement. Calculer `AssertionSummary` a partir des echantillons deja collectes. |
| `completeCurrentCycle` | Si un cycle de sampling est en cours (ex: requete HTTP vers WireMock en vol), attendre sa completion. Puis arreter et produire le resultat. |
| `gracePeriod` | Continuer le sampling pendant `gracePeriodDuration` apres le signal STOP. Puis arreter et produire le resultat. Utile pour capturer les effets residuels post-injection. |

Les stop behaviors sont parametres dans le YAML (`StepDefinition.parameters()`), lus par l'engine, et transmis dans `ExecutionLifecycleSignal.parameters`. L'agent les lit depuis le signal, pas depuis `step.parameters()`.

---

## agent.supported-tasks (configuration)

Pour les agents, les noms d'assertion doivent etre ajoutes a `agent.supported-tasks` dans les profils agent :

```yaml
# application-agent.yaml -- ajout des noms d'assertion
agent:
  supported-tasks:
    # ... taches existantes (performance_test, database, gatling, etc.)
    - gatling-metric
    - database
    - kafka
    - wiremock
    - http-mock
    - file
```

Les 6 noms d'assertion doivent apparaitre dans `application-agent.yaml`. L'ordre n'a pas d'importance.

---

## Regles de Comportement

- Le `DagPhaseExecutor` ne fait PLUS de distinction entre les phases pour la resolution d'executor. `findTaskExecutor()` est appele pour toutes les phases.
- `assertionResultToTaskResult()` n'est plus necessaire dans `DagPhaseExecutor` car la conversion est faite par `AssertionExecutor.execute()` (default method) avant que le resultat n'arrive a l'engine.
- **Lifecycle signals pour TOUTES les tasks** : START avant execution, STOP apres. Pas seulement pour les assertions.
- **Assertions avec `linkedTo`** : START envoye avant l'injection liee, STOP envoye apres la completion de l'injection.
- **Assertions sans `linkedTo`** : executees dans la phase ASSERTION normale (apres INJECTION). START/STOP encadrent l'execution.
- Le `RemoteExecutionEngine` peut desormais dispatcher les assertions sans code supplementaire : il envoie des `TaskExecutionRequest` comme pour toute autre tache, les agents les resolvent via `TaskExecutionPipeline.taskExecutors.get(taskName)`.
- Les agents qui declarent `gatling-metric` dans `supported-tasks` recevront et executeront les assertions Gatling.
- En mode LOCAL, les assertions continuent de tourner sur le `LocalAgent` (meme JVM que l'orchestrateur).
- En mode DISTRIBUTED, les assertions tournent sur les agents qui les declarent dans `supported-tasks`.
- `ExecutionLifecycleSignal` est broadcast via `transport.broadcastSignal()`. Tous les agents le recoivent. L'agent specialise pour le `taskId` du signal le traite ; les autres l'ignorent silencieusement.
- `stopBehavior` par defaut = `immediate` si non specifie dans le YAML.
- Si `gracePeriodDuration` est specifie mais `stopBehavior` n'est pas `gracePeriod`, le parametre est ignore (log warning).

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-006 (ExecutionPlanBuilder, DagPhaseExecutor)  -> deja DONE
  PDR-035 (AssertionExecutor extends TaskExecutor)   -> unified lookup
  PDR-036 (AssertionResultMapper)                    -> conversion utilitaire
  PDR-038 (ExecutionLifecycleSignal)                 -> signal domain type (NOUVEAU)

Ce PDR est utilise par :
  (aucun -- PDR terminal dans la chaine d'assertions distribuees)
```

---

## Criteres de Done (PDR complet)

- [ ] `DagPhaseExecutor` utilise le chemin unifie pour toutes les phases (ISSUE-154)
- [ ] `executeAssertionStep()` supprimee, fusionnee dans `executeStep()` (ISSUE-154)
- [ ] `assertionResultToTaskResult()` supprimee de `DagPhaseExecutor` (ISSUE-154)
- [ ] `TaskExecutorLookup.findAssertionExecutor()` deprecie (ISSUE-155)
- [ ] `ExecutionLifecycleDispatcher` implemente pour LOCAL et DISTRIBUTED (ISSUE-158)
- [ ] Engine envoie START/STOP autour de chaque execution de task (ISSUE-158)
- [ ] Engine coordonne les assertions avec `linkedTo` : START avant injection, STOP apres (ISSUE-158)
- [ ] `ExecutionPlan.assertionIntervalSteps` peuple par le builder (ISSUE-158)
- [ ] Agent recoit et traite `ExecutionLifecycleSignal` (ISSUE-159)
- [ ] Agent gere `stopBehavior` : immediate, completeCurrentCycle, gracePeriod (ISSUE-159)
- [ ] `application-agent.yaml` liste les 6 noms d'assertion (ISSUE-156)
- [ ] `mvn test -pl platform-execution-engine -q` -> 0 erreur
- [ ] `mvn test -pl platform-agent-runtime -q` -> 0 erreur
- [ ] Test d'integration : assertion avec linkedTo en mode LOCAL
- [ ] Test d'integration : assertion avec linkedTo en mode DISTRIBUTED

# ISSUE-143 — Engine implémente les ports in + suppression du port ExecutionEngine + config possédée

**PDR** : PDR-033
**Module** : `platform-execution-engine`
**Statut** : DONE
**Priorité** : P1 (critique — supprime la colle d'assemblage et corrige le bug LOCAL)
**Bloquée par** : ISSUE-134 (classpath Spring cohérent via BOM)
**Estime** : L (3-6h)

---

## Objectif

Appliquer ADR-026 D1 côté engine : `LocalExecutionEngine` et `RemoteExecutionEngine`
**implémentent directement** `ExecuteScenarioUseCase` + `CancelExecutionUseCase` (signatures
déjà identiques) ; **supprimer le port redondant `ExecutionEngine`**. Faire posséder par le
module engine son `ExecutionConfig` (binding `@ConfigurationProperties`). Rendre les beans
orchestrateur-only conditionnels DISTRIBUTED (corrige le démarrage LOCAL).

## Fichiers à Créer / Modifier

```
MODIFIER (main) :
platform-execution-engine/.../engine/local/LocalExecutionEngine.java
  — implements ExecuteScenarioUseCase, CancelExecutionUseCase   (au lieu de ExecutionEngine)
    (execute/cancel déjà présents ; getStatus reste méthode interne si utilisée, sinon retirer)
platform-execution-engine/.../engine/remote/RemoteExecutionEngine.java
  — implements ExecuteScenarioUseCase, CancelExecutionUseCase
platform-execution-engine/.../engine/availability/DefaultAgentAvailabilityChecker.java
  — @ConditionalOnProperty(name="runtime.mode", havingValue="DISTRIBUTED")
platform-execution-engine/.../engine/correlation/DefaultTaskCorrelationTracker.java
  — @ConditionalOnProperty(name="runtime.mode", havingValue="DISTRIBUTED")

SUPPRIMER (main) :
platform-execution-engine/.../engine/ExecutionEngine.java   — port redondant (ADR-026)

CRÉER (main) :
platform-execution-engine/.../engine/config/ExecutionEngineConfiguration.java
  — @Configuration ; @Bean @ConditionalOnProperty(runtime.mode=DISTRIBUTED) ExecutionConfig executionConfig(...)
  — (optionnel) ExecutionEngineProperties @ConfigurationProperties pour binder les valeurs

MODIFIER (test) :
platform-execution-engine/src/test/...  — tests référençant ExecutionEngine → ports in
```

## Interfaces à Implémenter

```java
// Ports in EXISTANTS (platform-application) — signatures inchangées, juste implémentées par l'engine
public interface ExecuteScenarioUseCase { ExecutionId execute(ScenarioDefinition s) throws ExecutionException; }
public interface CancelExecutionUseCase { void cancel(ExecutionId id); }

@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
public class LocalExecutionEngine implements ExecuteScenarioUseCase, CancelExecutionUseCase {
    // constructeur inchangé (planBuilder, retryExecutor, executionRepository, eventPublisher, taskExecutorLookup)
    @Override public ExecutionId execute(ScenarioDefinition s) throws ExecutionException { /* existant */ }
    @Override public void cancel(ExecutionId id) { /* existant */ }
}

@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
public class RemoteExecutionEngine implements ExecuteScenarioUseCase, CancelExecutionUseCase {
    // dépend de ExecutionConfig (fourni par ExecutionEngineConfiguration)
}

@Configuration
public class ExecutionEngineConfiguration {
    @Bean
    @ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
    public ExecutionConfig executionConfig(/* ExecutionEngineProperties */) {
        return new ExecutionConfig(/* défauts : TaskCompletionPolicy, timeouts... */);
    }
}
```

## Règles Spécifiques

- **Suppression d'`ExecutionEngine` actée par ADR-026** (CC-04). Vérifier qu'aucun appelant hors
  tests ne référence le type ; les appelants applicatifs injectent désormais les **ports in**.
- **`getStatus`** : n'est PAS un port porté par l'engine (read-model séparé, ISSUE-146). Si la
  méthode `getStatus` interne de l'engine n'est plus utilisée, la retirer ; sinon la garder
  comme méthode privée/interne, pas comme implémentation de port.
- **`ExecutionConfig`** : le record reste **framework-free** dans `platform-application` ; seul
  le *binding/bean* vit dans l'engine. Valeurs par défaut cohérentes (vérifier les champs réels
  du record). Conditionnel DISTRIBUTED (seul `RemoteExecutionEngine` en a besoin).
- **Beans orchestrateur-only conditionnels DISTRIBUTED** : `DefaultAgentAvailabilityChecker`
  (dépend de `AgentRegistryPort`) et `DefaultTaskCorrelationTracker`. Corrige le bug : en LOCAL,
  ces beans ne doivent pas être instanciés (sinon `AgentRegistryPort` manquant → échec).
- **`DefaultExecutionPlanBuilder` / `DefaultRetryExecutor`** restent inconditionnels (partagés /
  inoffensifs).
- `@ConditionalOnProperty` uniquement (pas `@Profile`).

## Critères de Done

- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur (tests adaptés à la suppression d'ExecutionEngine)
- [ ] `ExecutionEngine.java` supprimé ; `grep -rn "ExecutionEngine\b" --include=*.java` → plus aucune référence de type (hors historique)
- [ ] `LocalExecutionEngine`/`RemoteExecutionEngine` `implements ExecuteScenarioUseCase, CancelExecutionUseCase`
- [ ] `ExecutionConfig` exposé en bean DISTRIBUTED par l'engine ; record toujours framework-free dans application
- [ ] `DefaultAgentAvailabilityChecker` + `DefaultTaskCorrelationTracker` conditionnels DISTRIBUTED
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` : `ExecutionEngine` → `❌ REMOVED (ADR-026)` ; engines = use cases
</content>

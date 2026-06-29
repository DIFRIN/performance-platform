# ISSUE-144 — Composition root mince (platform-app) : glue lookup + scenario parsing + status + smoke tests

**PDR** : PDR-033
**Module** : `platform-app`
**Statut** : WAITING
**Priorité** : P1 (critique — assemble les modules framework-free + valide le démarrage)
**Bloquée par** : ISSUE-143, ISSUE-145, ISSUE-146, ISSUE-147
**Estime** : L (3-6h)

---

## Objectif

Réduire `platform-app` à un **composition root mince** (ADR-026) : ne câbler QUE ce qui ne peut
pas être self-wiré, à savoir (1) la glue cross-module `TaskExecutorLookup`, (2) le câblage des
modules **framework-free** (`ScenarioParsingUseCase` depuis scenario-dsl, `GetExecutionStatusUseCase`
depuis le read-model d'ISSUE-146). Plus **aucun** `@Bean` d'adaptation execute/cancel (les
engines implémentent les ports — ISSUE-143), ni `ExecutionConfig`/`AgentRegistry` (possédés par
leurs modules — ISSUE-143/147). Ajouter les smoke tests de démarrage des 3 modes.

## Fichiers à Créer / Modifier

```
CRÉER (main) :
platform-app/src/main/java/com/performance/platform/app/config/
  ├── RuntimeAssemblyConfiguration.java     — @Bean TaskExecutorLookup (glue), ScenarioParsingUseCase
  │                                            (+ YamlScenarioParser + ScenarioValidator), GetExecutionStatusUseCase
  └── RegistryTaskExecutorLookup.java        — implements TaskExecutorLookup (bridge registres)

CRÉER (test) :
platform-app/src/test/java/com/performance/platform/app/config/
  ├── LocalContextStartupTest.java           — SpringApplicationBuilder(NONE) runtime.mode=LOCAL (H2)
  ├── OrchestratorContextStartupTest.java    — DISTRIBUTED/ORCHESTRATOR, transport.type=IN_MEMORY
  └── AgentContextStartupTest.java           — MODE=AGENT, aucun bean DB/web, DistributedAgentRuntime présent
```

## Interfaces à Implémenter

```java
public final class RegistryTaskExecutorLookup implements TaskExecutorLookup {
    private final TaskExecutorRegistry taskRegistry;             // @Component (infra)
    private final AssertionExecutorRegistry assertionRegistry;  // @Component (assertion)
    public RegistryTaskExecutorLookup(TaskExecutorRegistry t, AssertionExecutorRegistry a) { ... }
    @Override public TaskExecutor findTaskExecutor(String name) {
        try { return taskRegistry.getFor(name); }
        catch (UnsupportedTaskNameException e) { return null; }
    }
    @Override public AssertionExecutor findAssertionExecutor(String name) {
        // résoudre via AssertionExecutorRegistry ; null si absent (vérifier la signature exacte)
    }
}

@Configuration
public class RuntimeAssemblyConfiguration {

    // (1) Glue cross-module irréductible — seul platform-app dépend de engine+infra+assertion (ADR-026)
    @Bean
    public TaskExecutorLookup taskExecutorLookup(TaskExecutorRegistry t, AssertionExecutorRegistry a) {
        return new RegistryTaskExecutorLookup(t, a);
    }

    // (2) Câblage du module framework-free scenario-dsl (D2)
    @Bean public YamlScenarioParser yamlScenarioParser() { return new YamlScenarioParser(); }
    @Bean public ScenarioValidator scenarioValidator() { return new DefaultScenarioValidator(); }
    @Bean public ScenarioParsingUseCase scenarioParsingUseCase(YamlScenarioParser p, ScenarioValidator v) {
        return new DefaultScenarioParsingService(p, v);
    }

    // (3) Câblage du read-model framework-free (ISSUE-146)
    @Bean public GetExecutionStatusUseCase getExecutionStatusUseCase(ExecutionRepository repo) {
        return new GetExecutionStatusService(repo);
    }
}
```

> NB : `ExecuteScenarioUseCase` et `CancelExecutionUseCase` ne sont **PAS** câblés ici — ils
> sont fournis par l'engine actif (ISSUE-143). `ExecutionConfig`/`AgentRegistry` non plus
> (ISSUE-143/147).

## Règles Spécifiques

- **Racine mince** : ne créer ici QUE les 3 catégories ci-dessus. Si un bean peut être self-wiré
  dans son module, il ne va PAS ici.
- **`TaskExecutorLookup`** : exception assumée (glue cross-module, ADR-026). `UnsupportedTaskNameException`
  → `null`. Vérifier la méthode de résolution de `AssertionExecutorRegistry`.
- **Ne pas annoter** scenario-dsl / application (restent framework-free).
- **Smoke tests `SpringApplicationBuilder`** (pas Testcontainers, pas `@SpringBootTest`) :
  ```java
  // LOCAL
  ctx = new SpringApplicationBuilder(PerformancePlatformApplication.class).web(NONE)
      .properties("runtime.mode=LOCAL","transport.type=IN_MEMORY","spring.profiles.active=local").run();
  assertThat(ctx.getBeanNamesForType(ExecuteScenarioUseCase.class)).isNotEmpty();   // = LocalExecutionEngine
  assertThat(ctx.getBeanNamesForType(GetExecutionStatusUseCase.class)).isNotEmpty();
  assertThat(ctx.getBeanNamesForType(TaskExecutorLookup.class)).isNotEmpty();
  // ORCHESTRATOR : RemoteExecutionEngine + ExecutionConfig + AgentRegistry présents (transport IN_MEMORY de test)
  // AGENT : web NONE, getBeanNamesForType(DataSource)/EntityManagerFactory vides, DistributedAgentRuntime présent
  ctx.close();
  ```
- LOCAL utilise H2 (profil local) ; ORCHESTRATOR force `transport.type=IN_MEMORY` (éviter Kafka) ;
  AGENT démarre sans DB (grâce à ISSUE-145).
- Si un smoke test révèle un bean ORCHESTRATOR manquant (au-delà d'ExecutionConfig/AgentRegistry),
  le câbler dans son **module propriétaire**, pas ici.

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur
- [ ] Contexte démarre en **LOCAL** / **ORCHESTRATOR** / **AGENT** (3 smoke tests verts)
- [ ] `platform-app` ne contient AUCUN `@Bean` execute/cancel, ni `ExecutionConfig`/`AgentRegistry`
- [ ] `ScenarioController` / `CliScenarioRunner` se câblent (ports in résolus)
- [ ] Aucune annotation Spring ajoutée dans scenario-dsl / application
- [ ] E2E existants verts (ou adaptés)
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` : beans d'assemblage racine documentés
</content>

# PDR-033 — Assemblage runtime : self-wiring par module + composition root mince

**Module Maven** : `platform-execution-engine`, `platform-application`, `platform-agent-runtime`,
`platform-app`, `platform-infrastructure`
**Package** : selon module propriétaire (voir découpage)
**Statut** : WAITING
**Specs de référence** : **ADR-026** (stratégie d'assemblage — D1/D2), ADR-025 (beans
conditionnels), ADR-019 (AGENT = `WebApplicationType.NONE`), ADR-021 (CLI headless),
specs 00-overview (matrice modes), CF-01/CF-03/CC-04, CLAUDE.md §5
**Dépend de** : PDR-030 (classpath Spring cohérent)
**Issues** : ISSUE-143, ISSUE-144, ISSUE-145, ISSUE-146, ISSUE-147

---

## Responsabilité

Rendre l'artefact unique réellement **démarrable en LOCAL / ORCHESTRATOR / AGENT à partir d'un
simple `application.yaml`**, en suivant la **stratégie d'assemblage d'ADR-026** : chaque module
adapter possède son câblage conditionnel ; la racine `platform-app` reste mince ; les
abstractions redondantes sont supprimées.

Décisions structurantes (ADR-026) :
- **D1** : les engines (`LocalExecutionEngine`/`RemoteExecutionEngine`) **implémentent
  directement** `ExecuteScenarioUseCase` + `CancelExecutionUseCase` ; le port redondant
  `ExecutionEngine` est **supprimé**. `GetExecutionStatusUseCase` devient un read-model
  framework-free (`GetExecutionStatusService`).
- **D2** : `platform-scenario-dsl` reste **framework-free** ; son câblage vit dans la racine.
- **Possession par module** : `ExecutionConfig` → engine ; `AgentRegistry` → agent-runtime ;
  beans orchestrateur-only de l'engine → conditionnels DISTRIBUTED ; datasource/JPA → infra.
- **Racine mince** : `platform-app` ne garde que la glue cross-module irréductible
  (`TaskExecutorLookup`), le câblage des modules framework-free (scenario parsing, status
  read-model) et l'existant (web/sécurité/CLI/runtime-mode).

Ce qu'il ne fait PAS : il ne touche pas la couche transport (déjà conditionnelle), ni
`AgentRuntimeConfiguration` (déjà correcte), ni la génération de rapport (déjà auto-câblée via
`DefaultReportEngine.@EventListener`). Il ne crée pas de `GenerateReportUseCase` bean.

---

## Interfaces Publiques

```java
// D1 — les engines implémentent les ports in (signatures déjà identiques) ; ExecutionEngine supprimé.
@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
public class LocalExecutionEngine implements ExecuteScenarioUseCase, CancelExecutionUseCase {
    public ExecutionId execute(ScenarioDefinition s) throws ExecutionException { ... }
    public void cancel(ExecutionId id) { ... }
}

@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
public class RemoteExecutionEngine implements ExecuteScenarioUseCase, CancelExecutionUseCase { ... }

// Read-model framework-free (platform-application)
public final class GetExecutionStatusService implements GetExecutionStatusUseCase {
    private final ExecutionRepository repository;
    public ExecutionStatus getStatus(ExecutionId id) {
        return repository.findById(id).map(ExecutionState::status).orElse(ExecutionStatus.STARTED);
    }
    public Optional<ExecutionState> getState(ExecutionId id) { return repository.findById(id); }
}

// AgentRegistry : un bean satisfait AgentRegistry ET AgentRegistryPort (extends)
@Configuration
public class AgentRegistryConfiguration {
    @Bean
    @ConditionalOnExpression("'${runtime.mode:LOCAL}'.equals('DISTRIBUTED') && '${runtime.role:NONE}'.equals('ORCHESTRATOR')")
    public AgentRegistry agentRegistry() { return new InMemoryAgentRegistry(); }
}

// Glue cross-module dans la racine (seul module dépendant engine+infra+assertion)
public final class RegistryTaskExecutorLookup implements TaskExecutorLookup {
    public TaskExecutor findTaskExecutor(String n) {
        try { return taskRegistry.getFor(n); } catch (UnsupportedTaskNameException e) { return null; }
    }
    public AssertionExecutor findAssertionExecutor(String n) { /* assertionRegistry ; null si absent */ }
}
```

---

## Règles de Comportement

- **`@ConditionalOnProperty`/`@ConditionalOnBean`/`@ConditionalOnExpression` uniquement** (CF-03).
- **Cœur framework-free inviolable** : `platform-application`, `platform-scenario-dsl`,
  `platform-domain`, `platform-plugin-api` → 0 annotation Spring.
- **Engines** : un seul bean de chaque port in par mode (déjà conditionnels). Les contrôleurs/CLI
  injectent les ports in directement.
- **Beans orchestrateur-only** (`DefaultAgentAvailabilityChecker`, `DefaultTaskCorrelationTracker`)
  → `@ConditionalOnProperty(runtime.mode=DISTRIBUTED)` (sinon LOCAL échoue).
- **`ExecutionConfig`** : binding possédé par l'engine ; le record reste framework-free dans
  application.
- **`AgentRegistry`** : possédé par agent-runtime, conditionnel ORCHESTRATOR ; un bean pour
  `AgentRegistry`/`AgentRegistryPort`.
- **datasource/JPA/repository** : conditionnels (infra, sans `throw`).
- **Tests = smoke tests `SpringApplicationBuilder`** (pas Testcontainers, pas `@SpringBootTest`).

---

## Découpage en Issues (module-pur)

| Issue | Module | Objet | Taille |
|---|---|---|---|
| ISSUE-143 | `platform-execution-engine` | Engines implémentent `ExecuteScenarioUseCase`+`CancelExecutionUseCase` ; suppression port `ExecutionEngine` ; `ExecutionConfig` via `@ConfigurationProperties` (DISTRIBUTED) ; availability checker + correlation tracker conditionnels DISTRIBUTED | L |
| ISSUE-146 | `platform-application` | `GetExecutionStatusService` (read-model framework-free) implements `GetExecutionStatusUseCase` | S |
| ISSUE-147 | `platform-agent-runtime` | `@Configuration` conditionnelle exposant `AgentRegistry` (ORCHESTRATOR) | S |
| ISSUE-145 | `platform-infrastructure` | `dataSource`/JPA/`JpaExecutionRepository` conditionnels (sans throw) + registries Kafka/HTTP | M |
| ISSUE-144 | `platform-app` | Composition root mince : `RegistryTaskExecutorLookup` glue + câblage scenario parsing (framework-free) + câblage `GetExecutionStatusUseCase` + smoke tests LOCAL/ORCHESTRATOR/AGENT | L |

Ordre : 143, 145, 146, 147 parallélisables (`← ISSUE-134` pour celles touchant le classpath) ;
**ISSUE-144 dépend de 143, 145, 146, 147** (la racine assemble tout).

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-030 (BOM Spring cohérent), ports in/out (platform-application),
  TaskExecutorRegistry (infra) + AssertionExecutorRegistry (assertion),
  YamlScenarioParser + DefaultScenarioValidator (scenario-dsl, framework-free),
  InMemoryAgentRegistry (agent-runtime), ExecutionConfig (application, record framework-free)
  Modèle de référence : TransportConfiguration (self-wiring conditionnel)

Ce PDR est utilisé par :
  ScenarioController, CliScenarioRunner, ExecutionController (injectent les ports in)
  Démarrage de l'app dans les 3 modes
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues DONE (143, 144, 145, 146, 147)
- [ ] Port `ExecutionEngine` supprimé ; engines implémentent `ExecuteScenarioUseCase`+`CancelExecutionUseCase`
- [ ] `GetExecutionStatusService` (framework-free) câblé ; `AgentRegistry` possédé par agent-runtime ;
      `ExecutionConfig` possédé par l'engine
- [ ] `platform-app` ne contient AUCUN `@Bean` d'adaptation execute/cancel ni `ExecutionConfig`/`AgentRegistry`
- [ ] App démarre en **LOCAL** (H2), **ORCHESTRATOR** (transport IN_MEMORY de test),
      **AGENT** (`WebApplicationType.NONE`, sans bean DB) — smoke tests verts (SpringApplicationBuilder)
- [ ] Beans orchestrateur-only absents en LOCAL ; aucun `IllegalStateException` datasource
- [ ] `mvn clean install` vert ; E2E existants verts (ou adaptés à la suppression d'`ExecutionEngine`)
- [ ] `.claude/workspace/interfaces-registry.md` : `ExecutionEngine` → REMOVED ; nouveaux beans documentés
</content>

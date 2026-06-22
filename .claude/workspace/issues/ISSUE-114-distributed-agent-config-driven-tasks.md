# ISSUE-114 — Wire DistributedAgentRuntime with config-driven supportedTaskNames

**PDR** : PDR-026
**Module** : `platform-app` (config wiring) + `platform-agent-runtime` (test verification)
**Statut** : WAITING
**Priorite** : P0 (DISTRIBUTED mode doit suivre ADR-015)
**Bloquee par** : ISSUE-112 (AgentRuntimeConfiguration doit exister)
**Estime** : M (1-3h)

---

## Objectif

Verifier que le cablage Spring du mode DISTRIBUTED (role AGENT) produit un `DistributedAgentRuntime` avec `supportedTaskNames` provenant EXCLUSIVEMENT de `AgentProperties.supportedTasks()` (c'est-a-dire de la configuration YAML ou de l'env var `AGENT_SUPPORTED_TASKS`). Les annotations des `TaskExecutor` NE sont PAS utilisees pour deriver `supportedTaskNames`.

Verifier egalement le comportement "agent idle" : si `agent.supported-tasks` est vide, l'agent logge un warning et ne traite aucune task.

## Fichiers a Creer / Modifier

```
platform-agent-runtime/src/test/java/com/performance/platform/agent/runtime/
  └── DistributedAgentConfigDrivenTest.java     — test verification config-driven

platform-app/src/test/java/com/performance/platform/app/config/
  └── AgentRuntimeConfigurationTest.java        — UPDATE: ajouter tests DISTRIBUTED config-driven
```

## Test `DistributedAgentConfigDrivenTest`

Test unitaire qui verifie que `DistributedAgentRuntime` n'utilise que les `supportedTaskNames` de son `AgentDescriptor` (pas de scan d'annotations) :

```java
@Test
void distributedAgentShouldOnlyUseConfiguredTaskNames() {
    // Given: 3 executors dans le registre, mais seulement 2 dans la config
    var exec1 = mock(TaskExecutor.class);
    when(exec1.getSupportedTaskName()).thenReturn("mock-server");
    var exec2 = mock(TaskExecutor.class);
    when(exec2.getSupportedTaskName()).thenReturn("gatling");
    var exec3 = mock(TaskExecutor.class);
    when(exec3.getSupportedTaskName()).thenReturn("http-client");

    var configuredNames = Set.of("mock-server", "http-client"); // gatling PAS inclus
    var filter = new DefaultTaskSpecializationFilter(configuredNames, AgentId.generate());
    var transport = mock(ExecutionTransport.class);
    var registrationPort = mock(AgentRegistrationPort.class);

    var descriptor = new AgentDescriptor(
            AgentId.generate(), "test-agent", "localhost", 8080, null,
            configuredNames, AgentCapabilities.empty(),
            AgentState.OFFLINE, Instant.now(), Instant.now(), Duration.ofMinutes(5));

    var agent = new DistributedAgentRuntime(
            transport, filter, registrationPort, descriptor,
            Duration.ofSeconds(10), Duration.ofMinutes(5),
            List.of(exec1, exec2, exec3), List.of());

    // Then: canExecute() reflete uniquement la config
    assertThat(agent.canExecute("mock-server")).isTrue();
    assertThat(agent.canExecute("http-client")).isTrue();
    assertThat(agent.canExecute("gatling")).isFalse(); // pas dans la config
}

@Test
void filterShouldRejectTaskNotInConfiguredNames() {
    var configuredNames = Set.of("mock-server");
    var filter = new DefaultTaskSpecializationFilter(configuredNames, AgentId.generate());

    // Step avec taskName "gatling" → pas dans la config
    var stepDef = new StepDefinition(
            TaskId.generate(), "gatling", Phase.INJECTION,
            List.of(), List.of(), Map.of(), null);
    var request = new TaskExecutionRequest(
            MessageId.generate(), ExecutionId.generate(),
            ScenarioId.generate(), stepDef,
            PartialExecutionContext.empty());

    var result = filter.filter(request);
    assertThat(result).isInstanceOf(TaskFilterResult.NotResponsible.class);
}
```

## Test d'integration Spring (platform-app)

Ajouter dans `AgentRuntimeConfigurationTest` :

```java
@Test
void distributedAgentShouldUseConfiguredTaskNames() {
    var context = new ApplicationContextRunner()
            .withUserConfiguration(AgentRuntimeConfiguration.class)
            .withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT",
                    "agent.supported-tasks[0]=mock-server",
                    "agent.supported-tasks[1]=http-client"
            )
            .withBean(ExecutionTransport.class, () -> mock(ExecutionTransport.class))
            .withBean(AgentRegistrationPort.class, () -> mock(AgentRegistrationPort.class))
            .withBean(TaskExecutorRegistry.class, () -> {
                var registry = mock(TaskExecutorRegistry.class);
                // 3 executors dans le registre
                var exec1 = mock(TaskExecutor.class);
                when(exec1.getSupportedTaskName()).thenReturn("mock-server");
                var exec2 = mock(TaskExecutor.class);
                when(exec2.getSupportedTaskName()).thenReturn("gatling");
                var exec3 = mock(TaskExecutor.class);
                when(exec3.getSupportedTaskName()).thenReturn("http-client");
                when(registry.getAll()).thenReturn(List.of(exec1, exec2, exec3));
                return registry;
            })
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(AgentRuntime.class);
                var agent = ctx.getBean(AgentRuntime.class);
                assertThat(agent).isInstanceOf(DistributedAgentRuntime.class);

                var taskNames = agent.getDescriptor().supportedTaskNames();
                // Seulement les 2 de la config, PAS "gatling" du registre
                assertThat(taskNames).containsExactlyInAnyOrder("mock-server", "http-client");
                assertThat(taskNames).doesNotContain("gatling");

                assertThat(agent.canExecute("mock-server")).isTrue();
                assertThat(agent.canExecute("http-client")).isTrue();
                assertThat(agent.canExecute("gatling")).isFalse();
            });
}

@Test
void distributedAgentWithEmptyConfigShouldBeIdle() {
    var context = new ApplicationContextRunner()
            .withUserConfiguration(AgentRuntimeConfiguration.class)
            .withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT"
                    // agent.supported-tasks PAS defini → liste vide
            )
            .withBean(ExecutionTransport.class, () -> mock(ExecutionTransport.class))
            .withBean(AgentRegistrationPort.class, () -> mock(AgentRegistrationPort.class))
            .withBean(TaskExecutorRegistry.class, () -> {
                var registry = mock(TaskExecutorRegistry.class);
                when(registry.getAll()).thenReturn(List.of());
                return registry;
            })
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(AgentRuntime.class);
                var agent = ctx.getBean(AgentRuntime.class);
                assertThat(agent.getDescriptor().supportedTaskNames()).isEmpty();

                // Aucune task acceptee
                assertThat(agent.canExecute("mock-server")).isFalse();
                assertThat(agent.canExecute("gatling")).isFalse();
            });
}
```

## Regles Specifiques

- `DistributedAgentRuntime.supportedTaskNames` = `AgentProperties.supportedTasks()` EXCLUSIVEMENT (ADR-015).
- Les `TaskExecutor` enregistres dans le `TaskExecutorRegistry` n'affectent pas `supportedTaskNames`.
- Si `agent.supported-tasks` est vide (pas de config) : `supportedTaskNames` = `Set.of()` → agent idle, log WARN.
- Le `TaskSpecializationFilter` utilise ces memes `supportedTaskNames` pour le filtrage local.
- Plugins charges mais non listes dans la config → idle (ADR-015).
- Le `PluginLoader` charge TOUS les executors de plugins, mais seuls ceux listes dans la config sont actives.
- Verifier avec `grep` que `@Preparation`/`@Injection`/`@Assertion` ne sont JAMAIS scannees pour deriver `supportedTaskNames` dans le code de configuration.

## Critères de Done

- [ ] `mvn test -pl platform-agent-runtime -q` → 0 erreur
- [ ] `mvn test -pl platform-app -q` → 0 erreur
- [ ] `DistributedAgentRuntime.canExecute("gatling")` = false si "gatling" n'est pas dans `agent.supported-tasks`
- [ ] Agent avec `agent.supported-tasks=[]` → `canExecute()` retourne false pour tout
- [ ] Aucun scan d'annotations pour deriver `supportedTaskNames` dans la couche config
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-114 → DONE

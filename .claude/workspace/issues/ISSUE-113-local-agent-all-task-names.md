# ISSUE-113 — Wire LocalAgent with ALL task names from TaskExecutorRegistry

**PDR** : PDR-026
**Module** : `platform-app` (config wiring) + `platform-agent-runtime` (test verification)
**Statut** : IN_REVIEW
**Priorite** : P0 (LOCAL mode doit fonctionner)
**Bloquee par** : ISSUE-112 (AgentRuntimeConfiguration doit exister)
**Estime** : M (1-3h)

---

## Objectif

Verifier que le cablage Spring du mode LOCAL produit un `LocalAgent` avec TOUS les task names du `TaskExecutorRegistry`. Le `LocalAgent` ignore `agent.supported-tasks` et derive `supportedTaskNames` du registre. Verifier par test unitaire que `LocalAgent.canExecute(taskName)` retourne `true` pour chaque task name enregistre.

Le code du `LocalAgent` est DEJA correct -- il utilise `descriptor.supportedTaskNames()` via `filter` et `canExecute()`. Le travail ici est de verifier le cablage et d'ajouter un test qui confirme le comportement.

## Fichiers a Creer / Modifier

```
platform-app/src/test/java/com/performance/platform/app/config/
  └── AgentRuntimeConfigurationTest.java   — UPDATE: ajouter test LOCAL all-task-names (si pas deja couvert dans ISSUE-112)

platform-agent-runtime/src/test/java/com/performance/platform/agent/local/
  └── LocalAgentAllTaskNamesTest.java      — test: LocalAgent avec registre connu, verifie canExecute()
```

## Verification du cablage

Dans `AgentRuntimeConfiguration.localAgentRuntime()` (ISSUE-112), le code collecte deja tous les noms du registre :

```java
var taskExecutors = taskExecutorRegistry.getAll();
var allTaskNames = taskExecutors.stream()
        .map(TaskExecutor::getSupportedTaskName)
        .collect(Collectors.toUnmodifiableSet());
```

Confirmer que ce `Set` est passe au `AgentDescriptor` et que le `LocalAgent` l'utilise.

## Test `LocalAgentAllTaskNamesTest`

Test unitaire (pas Spring) qui verifie le comportement du `LocalAgent` quand il est cree avec un `AgentDescriptor` contenant tous les task names :

```java
@Test
void localAgentShouldAcceptAllRegisteredTaskNames() {
    // Given: 3 TaskExecutors enregistres
    var exec1 = mock(TaskExecutor.class);
    when(exec1.getSupportedTaskName()).thenReturn("mock-server");
    var exec2 = mock(TaskExecutor.class);
    when(exec2.getSupportedTaskName()).thenReturn("gatling");
    var exec3 = mock(TaskExecutor.class);
    when(exec3.getSupportedTaskName()).thenReturn("http-client");

    var allTaskNames = Set.of("mock-server", "gatling", "http-client");
    var transport = new InMemoryExecutionTransport();
    var descriptor = new AgentDescriptor(
            AgentId.generate(), "local-agent", "localhost", 8080, null,
            allTaskNames, AgentCapabilities.empty(),
            AgentState.OFFLINE, Instant.now(), Instant.now(), Duration.ofMinutes(5));

    var agent = new LocalAgent(transport, descriptor,
            Duration.ofMinutes(5), List.of(exec1, exec2, exec3), List.of());

    // Then: canExecute() retourne true pour chaque task name
    assertThat(agent.canExecute("mock-server")).isTrue();
    assertThat(agent.canExecute("gatling")).isTrue();
    assertThat(agent.canExecute("http-client")).isTrue();

    // Et false pour une task non enregistree
    assertThat(agent.canExecute("unknown-task")).isFalse();
}

@Test
void localAgentShouldIgnoreAgentProperties() {
    // Le LocalAgent derive ses supportedTaskNames du registre,
    // pas de AgentProperties. Ce test confirme que meme avec
    // un supportedTaskNames partiel, canExecute() reflete le descriptor.
    var exec = mock(TaskExecutor.class);
    when(exec.getSupportedTaskName()).thenReturn("mock-server");
    var transport = new InMemoryExecutionTransport();
    var partialNames = Set.of("mock-server"); // seulement 1 sur 2
    var descriptor = new AgentDescriptor(
            AgentId.generate(), "partial-agent", "localhost", 8080, null,
            partialNames, AgentCapabilities.empty(),
            AgentState.OFFLINE, Instant.now(), Instant.now(), Duration.ofMinutes(5));

    var agent = new LocalAgent(transport, descriptor,
            Duration.ofMinutes(5), List.of(exec), List.of());

    assertThat(agent.canExecute("mock-server")).isTrue();
    assertThat(agent.canExecute("gatling")).isFalse(); // non inclus dans le descriptor
}
```

## Test d'integration Spring (dans platform-app)

Ajouter dans `AgentRuntimeConfigurationTest` (cree dans ISSUE-112) un test qui verifie que le `AgentRuntime` bean en LOCAL a bien le bon type ET que son `getDescriptor().supportedTaskNames()` contient tous les task names des executors enregistres :

```java
@Test
void localAgentShouldHaveAllTaskNamesFromRegistry() {
    var context = new ApplicationContextRunner()
            .withUserConfiguration(AgentRuntimeConfiguration.class)
            .withPropertyValues("runtime.mode=LOCAL")
            .withBean(InMemoryExecutionTransport.class, InMemoryExecutionTransport::new)
            .withBean(TaskExecutorRegistry.class, () -> {
                // Fake registry with known executors
                var registry = mock(TaskExecutorRegistry.class);
                var exec1 = mock(TaskExecutor.class);
                when(exec1.getSupportedTaskName()).thenReturn("mock-server");
                var exec2 = mock(TaskExecutor.class);
                when(exec2.getSupportedTaskName()).thenReturn("gatling");
                when(registry.getAll()).thenReturn(List.of(exec1, exec2));
                return registry;
            })
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(AgentRuntime.class);
                var agent = ctx.getBean(AgentRuntime.class);
                assertThat(agent).isInstanceOf(LocalAgent.class);
                var taskNames = agent.getDescriptor().supportedTaskNames();
                assertThat(taskNames).containsExactlyInAnyOrder("mock-server", "gatling");
                assertThat(agent.canExecute("mock-server")).isTrue();
                assertThat(agent.canExecute("gatling")).isTrue();
            });
}
```

## Regles Specifiques

- Le `LocalAgent` NE lit PAS `AgentProperties`. Le test doit le confirmer.
- Tous les `TaskExecutor` enregistres dans le `TaskExecutorRegistry` doivent etre dans `supportedTaskNames`.
- La methode `canExecute()` delegue a `descriptor.supportedTaskNames().contains()` -- c'est deja le comportement implemente.
- Le test d'integration utilise `ApplicationContextRunner` (Spring Boot Test) pour etre leger.

## Critères de Done

- [ ] `mvn test -pl platform-agent-runtime -q` → 0 erreur
- [ ] `mvn test -pl platform-app -q` → 0 erreur
- [ ] `LocalAgent.canExecute("mock-server")` = true si "mock-server" est dans le registre
- [ ] `LocalAgent.canExecute("unknown")` = false si "unknown" n'est pas dans le registre
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-113 → DONE

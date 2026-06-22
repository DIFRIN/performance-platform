# ISSUE-112: Create AgentRuntimeConfiguration @Configuration
**Status**: APPROVED
**PDR**: PDR-026
**Module**: platform-app
**Started**: 2026-06-22T18:42+02:00


**PDR** : PDR-026
**Module** : `platform-app`
**Statut** : WAITING
**Priorite** : P0 (bloquant ISSUE-113, ISSUE-114)
**Bloquee par** : ISSUE-111 (AgentProperties doit exister)
**Estime** : M (1-3h)

---

## Objectif

Creer la classe de configuration Spring `AgentRuntimeConfiguration` qui produit les beans du runtime agent selon le mode d'execution. En mode LOCAL, elle cree un `LocalAgent`. En mode DISTRIBUTED avec role AGENT, elle cree un `DistributedAgentRuntime`. Les beans sont conditionnels (`@ConditionalOnProperty`) pour eviter tout `if/switch` dans le code metier (CF-03).

## Fichiers a Creer

```
platform-app/src/main/java/com/performance/platform/app/config/
  └── AgentRuntimeConfiguration.java      — @Configuration avec @Bean conditionnels

platform-app/src/test/java/com/performance/platform/app/config/
  └── AgentRuntimeConfigurationTest.java  — tests de selection de beans selon mode
```

## Structure de la classe

```java
package com.performance.platform.app.config;

import com.performance.platform.agent.runtime.AgentRuntime;
import com.performance.platform.agent.filter.DefaultTaskSpecializationFilter;
import com.performance.platform.agent.filter.TaskSpecializationFilter;
import com.performance.platform.agent.registration.AgentRegistrationPort;
import com.performance.platform.agent.runtime.DistributedAgentRuntime;
import com.performance.platform.agent.local.LocalAgent;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.plugin.StatefulResourceCleaner;
import com.performance.platform.infrastructure.executor.TaskExecutorRegistry;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration Spring pour les beans du runtime agent.
 * <p>
 * Selectionne le {@link AgentRuntime} selon le mode :
 * <ul>
 *   <li>{@code runtime.mode=LOCAL} → {@code LocalAgent} avec tous les task names
 *       du {@code TaskExecutorRegistry}</li>
 *   <li>{@code runtime.mode=DISTRIBUTED} et {@code runtime.role=AGENT} →
 *       {@code DistributedAgentRuntime} avec {@code agent.supported-tasks}
 *       de la configuration</li>
 * </ul>
 * <p>
 * Aucun bean {@link AgentRuntime} n'est cree en mode ORCHESTRATOR.
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentRuntimeConfiguration {

    // ========================================================================
    // Mode LOCAL : LocalAgent avec TOUS les task names du registre
    // ========================================================================

    @Bean
    @ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
    public AgentRuntime localAgentRuntime(
            InMemoryExecutionTransport transport,
            TaskExecutorRegistry taskExecutorRegistry,
            ObjectProvider<StatefulResourceCleaner> cleanersProvider) {

        var taskExecutors = taskExecutorRegistry.getAll();
        var allTaskNames = taskExecutors.stream()
                .map(TaskExecutor::getSupportedTaskName)
                .collect(Collectors.toUnmodifiableSet());

        var agentId = AgentId.generate();
        var descriptor = new AgentDescriptor(
                agentId,
                "local-agent",
                "localhost",
                8080,
                null,
                allTaskNames,                    // TOUS les noms du registre
                AgentCapabilities.empty(),
                AgentState.OFFLINE,
                Instant.now(),
                Instant.now(),
                Duration.ofMinutes(5)
        );

        var cleaners = cleanersProvider.stream().toList();
        return new LocalAgent(transport, descriptor, Duration.ofMinutes(5), taskExecutors, cleaners);
    }

    // ========================================================================
    // Mode DISTRIBUTED, role AGENT : DistributedAgentRuntime avec config
    // ========================================================================

    @Bean
    @ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
    @ConditionalOnProperty(name = "runtime.role", havingValue = "AGENT")
    public AgentRuntime distributedAgentRuntime(
            ExecutionTransport transport,
            AgentProperties agentProperties,
            AgentRegistrationPort registrationPort,
            TaskExecutorRegistry taskExecutorRegistry,
            ObjectProvider<StatefulResourceCleaner> cleanersProvider) {

        var taskExecutors = taskExecutorRegistry.getAll();
        var supportedTaskNames = Set.copyOf(agentProperties.supportedTasks());

        if (supportedTaskNames.isEmpty()) {
            // Pas de tasks configurees → agent idle (log en WARN dans le runtime)
            log.warn("No agent.supported-tasks configured — agent will be idle");
        }

        var agentId = AgentId.generate();
        var descriptor = new AgentDescriptor(
                agentId,
                resolveAgentName(),
                resolveAgentHost(),
                resolveAgentPort(),
                null,
                supportedTaskNames,              // depuis la config
                AgentCapabilities.empty(),
                AgentState.OFFLINE,
                Instant.now(),
                Instant.now(),
                Duration.ofMinutes(5)
        );

        var filter = new DefaultTaskSpecializationFilter(supportedTaskNames, agentId);
        var cleaners = cleanersProvider.stream().toList();

        return new DistributedAgentRuntime(
                transport,
                filter,
                registrationPort,
                descriptor,
                Duration.ofSeconds(10),         // heartbeat interval
                Duration.ofMinutes(5),          // task execution timeout
                taskExecutors,
                cleaners
        );
    }

    // ========================================================================
    // TaskSpecializationFilter (partage, utilise par les beans ci-dessus)
    // ========================================================================

    @Bean
    @ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
    @ConditionalOnProperty(name = "runtime.role", havingValue = "AGENT")
    public TaskSpecializationFilter taskSpecializationFilter(
            AgentProperties agentProperties) {
        var agentId = AgentId.generate();
        return new DefaultTaskSpecializationFilter(
                Set.copyOf(agentProperties.supportedTasks()), agentId);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static String resolveAgentName() {
        // Priorite: env var AGENT_NAME > hostname > "agent-" + random
        var envName = System.getenv("AGENT_NAME");
        if (envName != null && !envName.isBlank()) return envName.strip();
        var envId = System.getenv("AGENT_ID");
        if (envId != null && !envId.isBlank()) return envId.strip();
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "agent-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private static String resolveAgentHost() {
        var envHost = System.getenv("AGENT_HOST");
        if (envHost != null && !envHost.isBlank()) return envHost.strip();
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static int resolveAgentPort() {
        var envPort = System.getenv("AGENT_PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort.strip());
        }
        return 8080;
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AgentRuntimeConfiguration.class);
}
```

## Regles Specifiques

- Les methodes `@Bean` utilisent `@ConditionalOnProperty` et non des `if/switch` (CF-03).
- Le `LocalAgent` obtient `allTaskNames` du `TaskExecutorRegistry` -- il ignore `AgentProperties.supportedTasks()`.
- Le `DistributedAgentRuntime` utilise exclusivement `AgentProperties.supportedTasks()` comme source de `supportedTaskNames` (ADR-015).
- `AgentId.generate()` est une methode statique existante sur le record `AgentId` -- si elle n'existe pas, utiliser `AgentId.of(java.util.UUID.randomUUID().toString())`.
- Les `StatefulResourceCleaner` sont injectes via `ObjectProvider` pour gerer le cas ou aucun cleaner n'est enregistre.
- `AgentCapabilities.empty()` : si la methode n'existe pas, utiliser `new AgentCapabilities(Set.of(), Map.of())` (a adapter selon la definition du record).
- Les helpers `resolveAgentName()`, `resolveAgentHost()`, `resolveAgentPort()` suivent la priorite env var > hostname > fallback (ADR-006).
- Verifier que `TaskExecutorRegistry` a bien une methode `getAll()`. Si elle s'appelle autrement (`listAll()`, `getExecutors()`...), utiliser le nom exact existant.
- Le `TaskSpecializationFilter` partage pour `DistributedAgentRuntime` doit etre le meme bean que celui utilise par l'agent. Dans le code ci-dessus, le `DistributedAgentRuntime` recoit son propre filtre cree localement. Le bean `taskSpecializationFilter` est disponible pour d'autres consommateurs (ex: observability).

## Tests

`AgentRuntimeConfigurationTest` (test Spring Boot avec `ApplicationContextRunner` ou `@SpringBootTest`):

1. **LOCAL mode** : avec `runtime.mode=LOCAL`, verifier qu'un bean `AgentRuntime` de type `LocalAgent` est cree.
2. **DISTRIBUTED+AGENT mode** : avec `runtime.mode=DISTRIBUTED` et `runtime.role=AGENT`, verifier qu'un bean `AgentRuntime` de type `DistributedAgentRuntime` est cree.
3. **ORCHESTRATOR mode** : avec `runtime.mode=DISTRIBUTED` et `runtime.role=ORCHESTRATOR`, verifier qu'AUCUN bean `AgentRuntime` n'est cree.
4. **Default empty supported-tasks** : en DISTRIBUTED+AGENT avec `agent.supported-tasks` non defini, verifier que le bean est cree avec une liste vide (agent idle).
5. **AgentProperties binding DISTRIBUTED** : avec `agent.supported-tasks: [mock-server]`, verifier que `AgentProperties.supportedTasks()` contient `"mock-server"`.

Utiliser `ApplicationContextRunner` pour des tests de configuration legers (sans demarrer l'appli entiere).

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur
- [ ] `AgentRuntimeConfiguration` est dans `platform-app/src/main/java/com/performance/platform/app/config/`
- [ ] Les tests verifient le bon type de bean selon le mode
- [ ] Aucun `if/switch` sur runtime.mode ou runtime.role dans la config
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-112 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis a jour : `AgentRuntimeConfiguration` → STABLE

## Reviewer Feedback
(None yet)

---
## Reviewer Feedback — 2026-06-22T19:33+02:00
Issue status is IN_PROGRESS, not IN_REVIEW — run issue-finish.sh first to transition. The committed script changes (ff59663) are infrastructure improvements, not an Issue implementation under review.

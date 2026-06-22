# PDR-026 — Agent Configuration Wiring & End-to-End Verification

**Module Maven** : `platform-app` (config beans), `platform-agent-runtime` (tests)
**Package** : `com.performance.platform.app.config`
**Statut** : WAITING
**Specs de reference** : `.claude/knowledge/adr/ADR-015-configuration-driven-agent-specialization.md`, `.claude/knowledge/specs/04-agent-runtime.md`
**Depend de** : PDR-009 (Agent Runtime — DONE), PDR-018 (Application Assembly — DONE), ADR-015 (ACCEPTED)
**Issues** : ISSUE-111, ISSUE-112, ISSUE-113, ISSUE-114, ISSUE-115, ISSUE-116, ISSUE-117, ISSUE-118

---

## Responsabilite

Ce PDR construit la couche de cablage Spring qui manque entre la configuration YAML (`agent.supported-tasks`) / variable d'environnement (`AGENT_SUPPORTED_TASKS`) et les beans du runtime agent (`LocalAgent`, `DistributedAgentRuntime`).

Le code metier est DEJA prêt : `DefaultTaskSpecializationFilter`, `LocalAgent`, et `DistributedAgentRuntime` acceptent tous `supportedTaskNames` en paramètre de constructeur. Les annotations `@Preparation`/`@Injection`/`@Assertion` sont DEJA correctement cantonnees au `PluginLoader`. Il manque UNIQUEMENT le cablage Spring qui :

1. Lit `agent.supported-tasks` depuis le YAML et/ou `AGENT_SUPPORTED_TASKS` depuis l'environnement
2. Cree les bons beans selon le mode (LOCAL vs DISTRIBUTED)
3. Passe les `supportedTaskNames` aux constructeurs des agents
4. Remplace toutes les occurrences de `AGENT_TAGS` par `AGENT_SUPPORTED_TASKS` dans les fichiers de deploiement

Ce PDR verifie aussi le comportement de bout en bout via des tests d'integration.

**Ce que ce PDR NE fait PAS** :
- Ne modifie pas les signatures `LocalAgent` ou `DistributedAgentRuntime` (elles sont correctes)
- Ne modifie pas `DefaultTaskSpecializationFilter` (il est correct)
- Ne modifie pas `PluginLoader`, `AnnotationScanner`, ou les annotations (cantonnees a PluginLoader, aucun changement)
- Ne modifie pas `AgentDescriptor` (le champ `supportedTaskNames` est un Set<String> neutre)
- Ne cree pas de nouveaux profils Spring ou modes de runtime

---

## Interfaces Publiques

### AgentProperties

```java
package com.performance.platform.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/**
 * Proprietes de configuration de l'agent, prefixe "agent".
 * <p>
 * En mode DISTRIBUTED (role AGENT), la liste {@code supportedTasks} definit
 * quelles tasks l'agent execute. L'env var {@code AGENT_SUPPORTED_TASKS}
 * (valeurs separees par des virgules) ecrase cette liste via le mecanisme
 * standard Spring Boot de binding de liste.
 * <p>
 * En mode LOCAL, cette propriete est ignoree : LocalAgent derive
 * automatiquement tous les task names du {@code TaskExecutorRegistry}.
 *
 * @param supportedTasks liste des noms de task que l'agent supporte
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(List<String> supportedTasks) {

    /** Defensive copy — immuable. Empty list par defaut. */
    public AgentProperties {
        supportedTasks = supportedTasks != null ? List.copyOf(supportedTasks) : List.of();
    }
}
```

### AgentRuntimeConfiguration

```java
package com.performance.platform.app.config;

import com.performance.platform.agent.runtime.AgentRuntime;
import com.performance.platform.agent.filter.TaskSpecializationFilter;
import com.performance.platform.agent.runtime.AgentLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Configuration Spring pour les beans du runtime agent.
 * <p>
 * Selection du {@link AgentRuntime} selon le mode :
 * <ul>
 *   <li>{@code runtime.mode=LOCAL} → {@code LocalAgent} avec tous les task names du registre</li>
 *   <li>{@code runtime.mode=DISTRIBUTED} + {@code runtime.role=AGENT} → {@code DistributedAgentRuntime}
 *       avec {@code agent.supported-tasks} de la config</li>
 * </ul>
 * <p>
 * Le {@link TaskSpecializationFilter} est cree a partir des supportedTaskNames
 * de l'{@link AgentDescriptor} (source = config, sauf LOCAL = registre).
 */
@Configuration
public class AgentRuntimeConfiguration {

    // --- Beans conditionnels au mode ---
    // Les signatures exactes sont detaillees dans les Issues 112, 113, 114.
    // Chaque methode @Bean est documentee dans son Issue respective.
}
```

Les signatures exactes des methodes `@Bean` sont detaillees dans les Issues (ISSUE-112, ISSUE-113, ISSUE-114). La classe `AgentRuntimeConfiguration` est un conteneur de configuration -- ses methodes sont decrites precisement dans les Issues.

---

## Regles de Comportement

- **LOCAL mode** : le bean `LocalAgent` ignore `agent.supported-tasks`. Il construit `supportedTaskNames` a partir du `TaskExecutorRegistry` (tous les noms enregistres). La propriete `agent.supported-tasks` peut exister ou non dans le profil local -- elle n'est pas lue.
- **DISTRIBUTED mode (role AGENT)** : le bean `DistributedAgentRuntime` utilise `AgentProperties.supportedTasks()` comme `supportedTaskNames`. Si la liste est vide, l'agent logge un warning et ne traite aucune task (idle).
- **DISTRIBUTED mode (role ORCHESTRATOR)** : aucun bean `AgentRuntime` n'est cree. L'orchestrateur n'execute pas de tasks directement.
- **`AGENT_SUPPORTED_TASKS` env var** : mappee automatiquement par Spring Boot via `@ConfigurationProperties`. Les valeurs sont separees par des virgules (binding standard Spring pour `List<String>` depuis une string). La priorite env var > property YAML est geree par Spring Boot nativement (aucun code custom necessaire, cf. CC-05 / ADR-013).
- **`AgentLifecycle` bean** : gere la registration, le start/stop dans le cycle de vie Spring (`@PostConstruct` / `@PreDestroy`). Expose l'agent via JMX/Actuator si applicable.
- **`TaskSpecializationFilter` bean** : cree avec `Set.copyOf(supportedTaskNames)`, partage entre l'agent et les composants qui en ont besoin.
- **`AGENT_TAGS` → `AGENT_SUPPORTED_TASKS`** : toute occurrence de `AGENT_TAGS` dans les fichiers de deploiement est remplacee. Le format change : au lieu de tags libres (`"dev,standard"`), on utilise des noms de task (`"mock-server,http-client"`).

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-009 (Agent Runtime)           → AgentRuntime, DistributedAgentRuntime, LocalAgent,
                                       TaskSpecializationFilter, DefaultTaskSpecializationFilter
  PDR-001 (Domain Core)             → AgentDescriptor, AgentId, AgentState
  PDR-010 (Task Executors)          → TaskExecutorRegistry
  PDR-018 (Application Assembly)    → RuntimeModeResolver, application-*.yaml
  ADR-015 (Configuration-Driven)    → regles de specialisation

Ce PDR est utilise par :
  PDR-025 (Mock Agent Demo)         → les scenarios dependent du cablage Spring pour fonctionner
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues ISSUE-111 a ISSUE-118 sont DONE
- [ ] `AgentProperties` est dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] `AgentRuntimeConfiguration` est dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] `mvn test -pl platform-app -q` passe (tests unitaires configuration)
- [ ] `mvn verify -P integration-tests` passe (E2E tests Testcontainers)
- [ ] `grep -r AGENT_TAGS` dans `platform-deployment/` retourne zero resultat
- [ ] `AGENT_SUPPORTED_TASKS` est la seule variable d'environnement utilisee pour la specialisation
- [ ] Mode LOCAL : LocalAgent accepte tous les task names du registre
- [ ] Mode DISTRIBUTED : DistributedAgentRuntime n'accepte que les task names de `agent.supported-tasks`

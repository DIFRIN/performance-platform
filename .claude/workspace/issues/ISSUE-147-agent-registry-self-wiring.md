# ISSUE-147 — AgentRegistry self-wiring conditionnel (platform-agent-runtime)

**PDR** : PDR-033
**Module** : `platform-agent-runtime`
**Statut** : APPROVED
**Priorité** : P1 (critique — l'orchestrateur a besoin d'AgentRegistryPort)
**Bloquée par** : ISSUE-134 (classpath Spring cohérent via BOM)
**Estime** : S (< 1h)

---

## Objectif

Faire en sorte que `platform-agent-runtime` **possède son câblage** : exposer
`InMemoryAgentRegistry` comme bean Spring, **conditionnel ORCHESTRATOR** (ADR-026). Un seul bean
satisfait à la fois `AgentRegistry` et `AgentRegistryPort` (`AgentRegistry extends
AgentRegistryPort`) — requis par `DefaultAgentAvailabilityChecker` (engine, DISTRIBUTED) et les
consommateurs du registre (ex. `AgentController`).

## Fichiers à Créer / Modifier

```
CRÉER (main) :
platform-agent-runtime/src/main/java/com/performance/platform/agent/registry/config/
  └── AgentRegistryConfiguration.java   — @Configuration ; @Bean conditionnel ORCHESTRATOR

CRÉER (test) :
platform-agent-runtime/src/test/java/com/performance/platform/agent/registry/config/
  └── AgentRegistryConfigurationTest.java   — vérifie la création conditionnelle (contexte slice ou condition)
```

## Interfaces à Implémenter

```java
@Configuration
public class AgentRegistryConfiguration {

    /**
     * Registre d'agents de l'orchestrateur. Un seul bean satisfait AgentRegistry
     * ET AgentRegistryPort (AgentRegistry extends AgentRegistryPort).
     * Conditionnel : uniquement en DISTRIBUTED + ORCHESTRATOR.
     */
    @Bean
    @ConditionalOnExpression(
        "'${runtime.mode:LOCAL}'.equals('DISTRIBUTED') && '${runtime.role:NONE}'.equals('ORCHESTRATOR')")
    public AgentRegistry agentRegistry() {
        return new InMemoryAgentRegistry();
    }
}
```

## Règles Spécifiques

- **Self-wiring** : le câblage du registre vit dans `platform-agent-runtime`, **pas** dans
  `platform-app` (ADR-026 — possession par le module).
- **Type exposé** : déclarer le bean en type `AgentRegistry` (sous-type d'`AgentRegistryPort`) —
  les deux points d'injection (`AgentRegistryPort` pour `DefaultAgentAvailabilityChecker`,
  `AgentRegistry` pour le registre lui-même) sont satisfaits par ce bean unique.
- **Conditionnel ORCHESTRATOR uniquement** : pas créé en LOCAL ni en AGENT. Cohérent avec le
  fait que `DefaultAgentAvailabilityChecker` est désormais conditionnel DISTRIBUTED (ISSUE-143) —
  en ORCHESTRATOR les deux existent, en LOCAL/AGENT aucun.
- **`InMemoryAgentRegistry` reste sans annotation** (c'est la `@Configuration` qui le câble) —
  ou, si préféré, retirer la Javadoc obsolète mentionnant « câblage… ISSUE-077/PDR-018 ».
- `@ConditionalOnExpression`/`@ConditionalOnProperty` uniquement (pas `@Profile`).
- Vérifier que `platform-agent-runtime` a bien `spring-context` + `spring-boot-autoconfigure`
  en dépendance (pour `@Configuration`/`@ConditionalOnExpression`) ; sinon les ajouter
  (versions héritées du BOM, ISSUE-134).

## Critères de Done

- [ ] `mvn test -pl platform-agent-runtime -q` → 0 erreur
- [ ] Bean `AgentRegistry` créé en ORCHESTRATOR, absent en LOCAL et AGENT
- [ ] Le bean satisfait l'injection de `AgentRegistryPort` (vérifié par le smoke test ORCHESTRATOR d'ISSUE-144)
- [ ] Câblage dans `platform-agent-runtime` (pas dans platform-app)
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` : `AgentRegistry` bean (agent-runtime, ORCHESTRATOR) documenté
</content>

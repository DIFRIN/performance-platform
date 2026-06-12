# ISSUE-038 — LocalAgent (mode LOCAL, toutes spécialisations)

**PDR** : PDR-009
**Module** : `platform-agent-runtime`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-036, ISSUE-027
**Estime** : M

---

## Objectif

Implémenter `LocalAgent` : agent unique en mode LOCAL qui déclare toutes les spécialisations
disponibles et utilise le transport in-memory.

## Fichiers à Créer

```
platform-agent-runtime/src/main/java/com/performance/platform/agent/local/
  └── LocalAgent.java

platform-agent-runtime/src/test/java/com/performance/platform/agent/local/
  └── LocalAgentTest.java — supporte tout, FIRST_COMPLETE
```

## Interfaces à Implémenter

```java
@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
public class LocalAgent implements AgentRuntime {
    // supportedTaskNames = tous les noms de TaskExecutorRegistry
    // transport = InMemoryExecutionTransport
    // filter = toujours RESPONSIBLE
    // completionPolicy = FIRST_COMPLETE
}
```

## Règles Spécifiques

- `supportedTaskNames` = tous les noms exposés par `TaskExecutorRegistry`.
- `canExecute(taskName)` retourne toujours true si l'executor existe.
- Utilise `InMemoryExecutionTransport`.
- Workflow identique au mode DISTRIBUTED (claim → exécute → result), mais 1 seul agent.

## Critères de Done

- [ ] `mvn test -pl platform-agent-runtime -q` → 0 erreur
- [ ] `LocalAgent` supporte toutes les tasks enregistrées
- [ ] Exécution via transport in-memory testée
- [ ] `progress.md` mis à jour : ISSUE-038 → DONE
- [ ] `context/interfaces-registry.md` : `LocalAgent` → STABLE

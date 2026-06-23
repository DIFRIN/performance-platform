# ISSUE-117 — End-to-end integration test: agent config → registration → task execution

**PDR** : PDR-026
**Module** : `platform-agent-runtime`
**Statut** : DONE
**Priorite** : P0 (verification bout-en-bout du modele configuration-driven)
**Bloquee par** : ISSUE-111, ISSUE-112, ISSUE-113, ISSUE-114, ISSUE-115, ISSUE-116 (TOUS doivent etre DONE)
**Estime** : L (3-6h)

---

## Objectif

Test d'integration complet (Testcontainers Kafka + PostgreSQL) qui verifie le flux bout-en-bout du modele configuration-driven en mode DISTRIBUTED :

1. Un orchestrateur demarre avec Kafka et PostgreSQL
2. Un agent demarre avec `agent.supported-tasks: [mock-server]` dans sa configuration
3. L'agent s'enregistre aupres de l'orchestrateur → `AgentLifecycleEvent(REGISTERED)` emis
4. L'orchestrateur dispatche une task `mock-server` via Kafka (broadcast)
5. L'agent recoit la task, `TaskSpecializationFilter` retourne `RESPONSIBLE`
6. L'agent execute la task via le bon `TaskExecutor`
7. Le `TaskResult` est publie en retour via Kafka
8. L'orchestrateur recoit l'evenement `TaskCompleted`

## Fichiers a Creer

```
platform-agent-runtime/src/test/java/com/performance/platform/agent/e2e/
  └── AgentConfigToExecutionE2ETest.java    — test E2E Testcontainers
```

## Test

```java
/**
 * Test E2E: un agent avec agent.supported-tasks=[mock-server] recoit
 * et execute une task mock-server, boucle complete.
 * <p>
 * Utilise Testcontainers pour Kafka + PostgreSQL.
 * L'orchestrateur et l'agent demarrent dans le meme test JVM
 * (Virtual Threads) pour simplifier, mais utilisent Kafka
 * comme transport (pas in-memory).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AgentConfigToExecutionE2ETest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    // Configuration du test
    static {
        System.setProperty("runtime.mode", "DISTRIBUTED");
        System.setProperty("transport.type", "KAFKA");
        System.setProperty("agent.supported-tasks[0]", "mock-server");
        // ...
    }

    // 1. Demarrer orchestrateur
    // 2. Demarrer agent avec agent.supported-tasks=[mock-server]
    // 3. Attendre AgentRegistered event
    // 4. Dispatcher TaskExecutionRequest(taskName="mock-server")
    // 5. Verifier TaskClaimedByAgent event
    // 6. Verifier TaskStarted event
    // 7. Verifier TaskCompleted event (avec result)
    // 8. Verifier qu'aucune task non-supportee n'est executee
}
```

La structure exacte du test (classes, mocks, assertions) est laissee au Developer, qui connait mieux les APIs Testcontainers et les imports exacts disponibles dans le projet.

## Scenarios de test

1. **task supportee executee** : `agent.supported-tasks=[mock-server]`, dispatch `mock-server` → `TaskCompleted` avec resultat OK.
2. **task non-supportee ignoree** : `agent.supported-tasks=[mock-server]`, dispatch `gatling` → agent loggue `NOT_RESPONSIBLE`, pas de `TaskClaimedByAgent`.
3. **agent idle (config vide)** : `agent.supported-tasks=[]`, dispatch `mock-server` → `NOT_RESPONSIBLE`.
4. **agent multi-task** : `agent.supported-tasks=[mock-server, http-client]`, dispatch `http-client` → `TaskCompleted`.
5. **enregistrement avec supportedTaskNames** : `AgentRegistered` event contient `supportedTaskNames` = config YAML.

## Regles Specifiques

- Utiliser `@Testcontainers` et `@Container` pour Kafka et PostgreSQL.
- Le test demarre l'orchestrateur et l'agent comme beans Spring separes (via `@SpringBootTest` avec profils appropries ou `ApplicationContext` multiples).
- Verifier que `AgentRegistered.supportedTaskNames()` correspond EXACTEMENT a `agent.supported-tasks` (pas de noms additionnels du registre).
- Le `TaskSpecializationFilter` est instancie avec `Set.copyOf(agentProperties.supportedTasks())`.
- Le test doit etre `@Tag("integration")` pour etre execute uniquement avec `mvn verify -P integration-tests`.
- Le test doit nettoyer les ressources (Kafka topics, DB) entre chaque test method.
- Suivre le pattern des tests d'integration existants dans le projet (ex: `KafkaExecutionTransport` tests dans `platform-transport`).

## Critères de Done

- [ ] `mvn verify -pl platform-agent-runtime -P integration-tests -q` → 0 erreur
- [ ] 5 scenarios de test passent
- [ ] `AgentRegistered.supportedTaskNames` = config YAML (pas registre)
- [ ] Task non-supportee = pas d'execution
- [ ] Test taggue `@Tag("integration")`
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-117 → DONE

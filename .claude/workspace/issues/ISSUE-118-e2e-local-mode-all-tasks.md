# ISSUE-118 — End-to-end integration test: LOCAL mode executes all scenario tasks

**PDR** : PDR-026
**Module** : `platform-app`
**Statut** : DONE
**Priorite** : P0 (verification bout-en-bout du mode LOCAL)
**Bloquee par** : ISSUE-113 (LocalAgent cablage doit etre DONE)
**Estime** : L (3-6h)

---

## Objectif

Test d'integration complet en mode LOCAL qui verifie :

1. La plateforme demarre en mode LOCAL
2. Un scenario YAML avec plusieurs types de tasks (`mock-server`, `gatling`, `http-client`) est charge
3. TOUTES les tasks sont executees sur l'instance locale unique (pas de dispatch)
4. Aucun Kafka n'est utilise (execution in-process avec transport InMemory)
5. Le rapport est genere (HTML/PDF/JSON)
6. `LocalAgent.canExecute()` retourne `true` pour chaque task name du scenario

## Fichiers a Creer

```
platform-app/src/test/java/com/performance/platform/e2e/
  └── LocalModeAllTasksE2ETest.java          — test E2E mode LOCAL

platform-app/src/test/resources/
  └── e2e-local-multi-tasks.yaml             — scenario YAML pour le test
```

## Scenario YAML de test

```yaml
# e2e-local-multi-tasks.yaml
# Scenario de test: mode LOCAL avec 3 types de tasks differentes.
# Verifie que le LocalAgent execute tout en-process, sans filtrage.

scenario:
  id: e2e-local-multi-tasks
  name: "E2E Local Mode — Multi-Task Execution"
  version: "1.0"
  completionPolicy: ALL_COMPLETE

phases:
  - name: preparation
    steps:
      - id: prep-mock
        task: mock-server
        config:
          port: 0  # auto-assign
          stubs:
            - path: /api/health
              response:
                status: 200
                body: '{"status":"ok"}'

  - name: injection
    steps:
      - id: inject-gatling
        task: gatling
        dependsOn: [prep-mock]
        config:
          simulation: test.SimpleSimulation
          duration: 5s
          concurrency: 2

  - name: assertion
    steps:
      - id: assert-http
        task: http-mock
        dependsOn: [inject-gatling]
        config:
          check: requests-count
          operator: GTE
          expected: 1
      - id: assert-gatling
        task: gatling-metric
        dependsOn: [inject-gatling]
        config:
          metric: p99ResponseTimeMs
          operator: LT
          expected: 1000
```

## Test

```java
/**
 * Test E2E mode LOCAL : un scenario multi-tasks est execute entierement
 * sur l'instance locale. Aucun Kafka, aucun dispatch reseau.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "runtime.mode=LOCAL",
        "transport.type=IN_MEMORY",
        "platform.security.enabled=false"
    }
)
@Tag("integration")
class LocalModeAllTasksE2ETest {

    // 1. Charger le scenario YAML e2e-local-multi-tasks.yaml
    // 2. Le soumettre via ScenarioParsingUseCase
    // 3. Executer via ExecuteScenarioUseCase
    // 4. Attendre la fin d'execution (poll ExecutionStatusUseCase)
    // 5. Verifier que toutes les tasks sont DONE
    // 6. Verifier que LocalAgent a execute toutes les tasks (pas de NOT_RESPONSIBLE)
    // 7. Generer le rapport via GenerateReportUseCase
    // 8. Verifier que le rapport contient toutes les tasks
    // 9. Verifier qu'aucun Kafka n'a ete utilise (pas de bean Kafka actif)
}
```

## Scenarios de test

1. **execution multi-task complete** : charger le scenario YAML, toutes les tasks passent.
2. **LocalAgent canExecute()** : verifier que `LocalAgent.canExecute("mock-server")` = true, `LocalAgent.canExecute("gatling")` = true, `LocalAgent.canExecute("http-mock")` = true.
3. **pas de filtrage** : aucune task n'est rejetee (pas de log `NOT_RESPONSIBLE`).
4. **transport in-memory** : verifier que le transport utilise est bien `InMemoryExecutionTransport`.
5. **rapport genere** : le rapport contient des entrees pour chaque task.
6. **agent ignore agent.supported-tasks** : meme si `agent.supported-tasks: [gatling]` est defini, toutes les tasks sont executees (LOCAL ignore cette propriete).

## Regles Specifiques

- Le test doit verifier explicitement que `LocalAgent` ignore la propriete `agent.supported-tasks`.
- Utiliser `InMemoryExecutionTransport` -- aucun conteneur Testcontainers necessaire (pas de Kafka).
- Si PostgreSQL est necessaire pour la persistence, utiliser Testcontainers PostgreSQL ou H2 (selon ce qui est deja configure dans le profil `application-local.yaml`).
- Le scenario YAML de test doit etre minimal mais couvrir les 3 phases (PREPARATION, INJECTION, ASSERTION).
- Tagger `@Tag("integration")`.
- Le test doit verifier que `LocalAgent.canExecute()` retourne `true` pour chaque task du scenario.
- Suivre le pattern du test E2E existant : `platform-app/src/test/.../LocalFlowE2ETest.java` (ISSUE-082, PDR-018).

## Critères de Done

- [ ] `mvn verify -pl platform-app -P integration-tests -q` → 0 erreur
- [ ] 6 scenarios de test passent
- [ ] Toutes les tasks du scenario YAML sont executees
- [ ] `LocalAgent.canExecute()` = true pour tous les task names
- [ ] Aucun Kafka n'est demarre/utilise
- [ ] Rapport genere contient toutes les tasks
- [ ] Test taggue `@Tag("integration")`
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-118 → DONE

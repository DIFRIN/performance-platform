---
name: spring-modulith
description: 'Use when assessing module boundaries and Spring event-based communication.'
---

# Skill — Frontières de modules (post-Spring Modulith)

> **Spring Modulith a été retiré du projet (ADR-035).** Ce fichier conserve son nom
> pour la table de routing, mais décrit désormais la règle réelle d'enforcement des
> frontières. Ne pas réintroduire `@ApplicationModule`, `@ApplicationModuleListener`
> ni `@ApplicationModuleTest`.

---

## Principe

Le projet est un **Maven multi-module** (14 modules). Les frontières ne sont pas des
packages d'un même artefact : ce sont des modules Maven avec leurs dépendances déclarées
dans les `pom.xml`.

Règles :
1. `platform-domain` et `platform-plugin-api` : 0 framework.
2. `platform-use-cases` et `platform-scenario-dsl` : 0 annotation Spring.
3. Les modules fonctionnels communiquent par **events de domaine**
   (`ApplicationEventPublisher` dans la JVM) ou par les **canaux de transport** entre
   JVMs — jamais par injection d'un service d'un autre module fonctionnel.
4. Les arêtes interdites sont vérifiées par `ModuleDependencyTest` (scan ASM des
   `target/classes`, test de non-vacuité).

## Communication inter-modules

```java
// ✅ Dans la JVM : événement de domaine
@Component
public class ExecutionEngine {
    private final ApplicationEventPublisher events;

    void complete(TaskResult result) {
        events.publishEvent(new TaskCompleted(...));
    }
}

// ✅ Listener standard Spring (PAS @ApplicationModuleListener)
@Component
public class ObservabilityEventListener {
    @EventListener
    public void on(TaskCompleted event) { ... }
}

// ✅ Entre JVMs : canaux de transport étroits
class RemoteStepDispatcher {
    private final TaskChannel transport;   // pas ExecutionTransport entier
}

// ❌ Interdit : injecter un service d'un autre module fonctionnel
class ExecutionEngine {
    private final ReportingService reporting;   // violation de frontière
}
```

## Enforcement

`bootstrap/src/test/java/com/performance/platform/arch/ModuleDependencyTest.java` :
- lit les `.class` via l'ASM de Spring 7 (compatible Java 25) ;
- vérifie les arêtes interdites (engine ↛ assertion/agent, reporting ↛ injection,
  assertion ↛ injection/infrastructure, etc.) ;
- échoue si un module analysé ne fournit aucune classe (protection anti-vacuité).

```bash
mvn -pl :platform-bootstrap test -Dtest=ModuleDependencyTest
```

> ⚠️ ArchUnit 1.3/1.4 importe 0 classe sur du bytecode Java 25 : les tests ArchUnit
> existants sont vacuous. Pour une nouvelle règle d'architecture, utiliser
> `ModuleDependencyTest` ou le même scanner ASM, pas ArchUnit.

## Déclaration des dépendances

Dans le `pom.xml` du module consommateur, uniquement les modules réellement nécessaires.
Exemple : `platform-execution-engine` dépend de `platform-domain`,
`platform-use-cases`, `platform-plugin-api` et `platform-transport` (ports), jamais de
`platform-assertion`, `platform-agent-runtime` ou `platform-reporting`.

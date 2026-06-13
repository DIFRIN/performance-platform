# Skill — Implémenter un TaskExecutor

> Ce skill décrit les patterns et règles pour ajouter un nouveau type de tâche.
> Aucune modification du code existant n'est nécessaire.
> Voir ADR-007 (plugin JAR) et ADR-008 (spécialisation par annotation).

---

## Étape 1 : Choisir l'annotation et le taskName

Décider :
- Quel type ? `@Preparation`, `@Injection`, ou `@Assertion`
- Quel `name` ? C'est la valeur que les scénarios YAML mettront dans `task:` et que les
  agents déclarent dans `agent.supportedTasks`

```java
// Exemples :
@Preparation(name = "my-db-seeder")    // → task: my-db-seeder dans le YAML
@Injection(name = "k6-load")           // → task: k6-load
@Assertion(name = "prometheus-check")  // → task: prometheus-check
```

Règle de nommage : kebab-case, évocateur, sans collision avec les noms internes
(voir `.claude/context/interfaces-registry.md` section platform-plugin-api).

---

## Étape 2 : Documenter les paramètres dans le PDR/Issue

Ajouter la section YAML dans le PDR ou l'Issue correspondante.

---

## Étape 3 : Implémenter l'executor

```java
// Composant interne : platform-infrastructure/adapter/out/task/
// Plugin externe  : projet séparé, dépendance sur platform-plugin-api uniquement

@Preparation(name = "mon-executor")   // ← annotation obligatoire — PAS @Component seul
public class MonTaskExecutor implements TaskExecutor {

    @Override
    public String getSupportedTaskName() {
        return "mon-executor";  // doit correspondre au name de l'annotation
    }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        var start = Instant.now();
        try {
            // 1. Extraire les paramètres depuis step.parameters()
            var param = (String) step.parameters().get("monParam");

            // 2. Accéder au contexte des steps précédents si nécessaire
            //    (uniquement les clés déclarées dans step.requiredContexts())
            var prevResult = context.getFirst("previous-step-id", Map.class);

            // 3. Exécuter la logique métier
            var output = doWork(param);

            // 4. Retourner le résultat — l'orchestrateur gère le merge dans le contexte global
            return TaskResult.success(
                TaskId.of(step.id().value()),
                getSupportedTaskName(),
                Duration.between(start, Instant.now()),
                Map.of("key", output)
            );
        } catch (Exception e) {
            return TaskResult.failed(
                TaskId.of(step.id().value()),
                getSupportedTaskName(),
                Duration.between(start, Instant.now()),
                "Échec : " + e.getMessage(),
                e
            );
        }
    }
}
```

---

## Étape 4 : Pour un plugin externe — packaging

```xml
<!-- pom.xml du projet plugin -->
<dependency>
    <groupId>com.performance</groupId>
    <artifactId>platform-plugin-api</artifactId>
    <version>PLATFORM_VERSION</version>
    <scope>provided</scope>
</dependency>
```

Packager en JAR fat et déposer dans `/plugins`. La plateforme charge automatiquement au démarrage.

---

## Étape 5 : Déclarer la spécialisation dans la config de l'agent

```yaml
agent:
  supportedTasks:
    - mon-executor    # correspond au name de l'annotation
```

---

## Étape 6 : Tests

```java
class MonTaskExecutorTest {

    @Test
    void shouldExecuteSuccessfully() {
        var executor = new MonTaskExecutor();
        var context  = ExecutionContext.initial(ExecutionId.generate(), ScenarioId.of("test"));
        var step     = new StepDefinition(
            TaskId.of("task-1"),
            "mon-executor",
            Phase.PREPARATION,
            Map.of("monParam", "valeur"),
            List.of(),
            List.of(),
            Duration.ofMinutes(1),
            null
        );

        var result = executor.execute(context, step);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outputs()).containsKey("key");
    }

    @Test
    void shouldReturnFailedOnException() {
        // Tester le cas d'erreur — TaskResult.failed() attendu, pas d'exception
    }
}
```

---

## Étape 7 : Mettre à jour le glossaire et l'interfaces-registry

Ajouter dans `.claude/context/interfaces-registry.md` sous le module concerné.

---

**C'est tout.** Aucun `if/switch`, aucun enum à modifier, aucun registre à toucher manuellement.
L'annotation est le contrat. Le `name` est la clé de résolution. Le JAR est le déploiement.

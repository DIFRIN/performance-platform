# ADR-007 — Système de Plugin JAR avec Annotations

**Date** : 2026-06-08
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : La plateforme doit permettre l'ajout de customisations (préparation,
injection, assertion, transport, publisher) via des JARs externes chargés au démarrage,
sans modifier le code de la plateforme.

---

## Décision

### 1. Chargement des JARs

Au démarrage, la plateforme scanne un répertoire configurable et charge les JARs
trouvés via un `URLClassLoader` enfant du classloader applicatif.

```yaml
platform:
  plugins:
    dir: /plugins          # répertoire scanné au boot
    enabled: true          # défaut true
```

Les classes annotées avec `@Preparation`, `@Injection`, ou `@Assertion` sont
découvertes par scan et enregistrées dans les registres correspondants.

---

### 2. Annotations de Plugin

Trois annotations distinctes, une par type de tâche :

```java
// Pour les tâches de préparation
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Preparation {
    String name();     // clé de résolution dans le registre et dans le YAML
    String version() default "1.0.0";
    String description() default "";
}

// Pour les tâches d'injection (load)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Injection {
    String name();
    String version() default "1.0.0";
    String description() default "";
}

// Pour les tâches d'assertion
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Assertion {
    String name();
    String version() default "1.0.0";
    String description() default "";
}
```

Les implémentations **internes** de la plateforme utilisent les mêmes annotations :

```java
// Interne — même mécanique que les plugins externes
@Preparation(name = "database", description = "DB operations: purge, populate, migrate")
public class DatabaseTaskExecutor implements TaskExecutor { ... }

@Injection(name = "gatling", description = "Gatling load injection")
public class GatlingTaskExecutor implements TaskExecutor { ... }

@Assertion(name = "gatling-metric", description = "Gatling metrics assertions")
public class GatlingMetricAssertionExecutor implements TaskExecutor { ... }
```

---

### 3. Matching DSL → Implémentation

Le YAML du scénario utilise `type` comme clé de résolution. La valeur de `type`
correspond au `name` de l'annotation :

```yaml
preparation:
  - id: purge
    type: database           # → @Preparation(name = "database") — interne

  - id: custom-seed
    type: my-custom-seeder   # → @Preparation(name = "my-custom-seeder") — plugin externe

injections:
  - id: load
    type: gatling            # → @Injection(name = "gatling") — interne

  - id: custom-load
    type: acme-grpc-load     # → @Injection(name = "acme-grpc-load") — plugin externe

assertions:
  - id: p95-check
    type: gatling-metric     # → @Assertion(name = "gatling-metric") — interne

  - id: custom-check
    type: my-custom-assertion  # → @Assertion(name = "my-custom-assertion") — plugin externe
```

**Pas de distinction interne/externe dans le YAML.** Le registre est unique et flat.
En cas de collision de `name` : le plugin externe écrase l'interne (override intentionnel).
Un warning est loggé si collision détectée.

---

### 4. Transport et Publisher via Properties

Les transports et publishers custom ne sont pas annotés — ils sont configurés
via propriétés avec le nom de classe qualifié ou le nom du bean Spring :

```yaml
# Transport custom
transport:
  type: CUSTOM
  custom-class: com.acme.MyCustomTransport   # doit implémenter ExecutionTransport

# Publisher custom
reporting:
  publishers:
    - target: CUSTOM
      custom-class: com.acme.MyCustomPublisher  # doit implémenter ReportPublisher
      properties:
        endpoint: https://my-system/reports
```

Spring instancie la classe via réflexion si pas déjà un bean, ou injecte le bean
par nom si c'est un bean Spring dans le plugin.

---

### 5. Interface que les Plugins Doivent Implémenter

Tout plugin (préparation, injection, assertion) doit implémenter `TaskExecutor` :

```java
// Contrat unique — interne ET externe
public interface TaskExecutor {
    TaskResult execute(ExecutionContext context, StepDefinition step);
    String getSupportedTaskName(); // correspond au name() de l'annotation
}
```

Transport custom → implémenter `ExecutionTransport`.
Publisher custom → implémenter `ReportPublisher`.

---

### 6. PluginLoader — Composant de Chargement

```java
public interface PluginLoader {
    /**
     * Charge tous les JARs du répertoire configuré.
     * Scanne les classes annotées @Preparation, @Injection, @Assertion.
     * Enregistre dans les registres correspondants.
     * Appelé une seule fois au démarrage, avant le contexte Spring principal.
     */
    PluginLoadResult load(Path pluginDirectory);
}

public record PluginLoadResult(
    int jarsLoaded,
    int executorsRegistered,
    List<String> warnings,    // collisions, classes non instanciables
    List<String> errors       // JARs invalides, classes manquantes
) {}
```

---

## Justification

- Annotations distinctes par type (`@Preparation`, `@Injection`, `@Assertion`) plutôt
  qu'une annotation générique : plus lisible dans les JARs plugins, erreur de type
  détectable à la compilation.
- Même mécanique interne/externe : pas de code dual-path dans le registre.
- Transport/Publisher via properties (pas annotations) : ces extensions sont rares,
  un seul actif à la fois, et la configuration YAML est plus naturelle pour un transport.
- Override intentionnel (externe écrase interne) : permet à un plugin de remplacer
  un comportement interne sans fork.

## Conséquences

- Le `TaskExecutorRegistry` doit être alimenté par le `PluginLoader` avant le démarrage
  du contexte Spring (ou via `ApplicationContextInitializer`).
- Les JARs plugins doivent avoir accès aux interfaces de la plateforme sur leur classpath
  (fournir un artifact `platform-plugin-api` avec uniquement les interfaces publiques).
- Un JAR plugin invalide génère un warning et est ignoré, pas un crash au démarrage.
- Le `ClassLoader` des plugins est isolé pour éviter les conflits de version de dépendances.

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Java SPI (`ServiceLoader`) | Nécessite `META-INF/services` dans chaque JAR — plus de friction pour les développeurs de plugins |
| Annotation unique `@TaskExecutor(type=PREPARATION)` | Moins lisible, type-safety réduite |
| Chargement à chaud (hot-reload) | Complexité classloader élevée, risques de fuite mémoire, hors scope |

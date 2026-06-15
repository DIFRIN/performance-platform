# Spec 03 — Task Framework

**Module** : `platform-infrastructure` (implémentations), `platform-domain` (interfaces)  
**Dépend de** : `platform-domain`, `platform-application`

---

## 1. Interface Principale

```java
public interface TaskExecutor {
    /**
     * Exécute la tâche et retourne un résultat immuable.
     * NE PAS lancer d'exception pour un échec métier → retourner TaskResult.failed()
     * Lancer une exception UNIQUEMENT pour erreur technique irrécupérable.
     *
     * Le paramètre StepDefinition remplace l'ancienne TaskDefinition.
     * taskName matche la spécialisation déclarée dans agent.supportedTasks.
     */
    TaskResult execute(ExecutionContext context, StepDefinition step);

    /**
     * Nom de la task supportée par cet executor.
     * Doit correspondre à la valeur de l'annotation @Preparation/@Injection/@Assertion.
     * Remplace getSupportedType() — plus de TaskType enum pour l'enregistrement.
     */
    String getSupportedTaskName();
}

public interface TaskExecutorRegistry {
    void register(TaskExecutor executor);
    TaskExecutor getFor(String taskName) throws UnsupportedTaskTypeException;
    Set<String> getSupportedTaskNames();
}
```

---

## 2. TaskResult

```java
public record TaskResult(
    TaskId taskId,
    TaskType taskType,
    TaskStatus status,                  // SUCCESS | FAILED | SKIPPED | TIMEOUT
    Duration duration,
    Map<String, Object> outputs,        // résultats stockés dans ExecutionContext
    String errorMessage,                // null si SUCCESS
    Throwable cause,                    // null si SUCCESS
    Instant completedAt
) {
    public static TaskResult success(TaskId id, TaskType type, Duration duration,
                                     Map<String, Object> outputs) { ... }
    public static TaskResult failed(TaskId id, TaskType type, Duration duration,
                                    String message, Throwable cause) { ... }
    public static TaskResult skipped(TaskId id, TaskType type, String reason) { ... }
    public boolean isSuccess() { return status == TaskStatus.SUCCESS; }
}
```

---

## 3. Types de Tâches et Paramètres YAML

### DATABASE
```yaml
type: DATABASE
operation: PURGE              # PURGE | POPULATE | MIGRATION | BACKUP | RESTORE
datasource: customer-db       # référence à une datasource configurée
table: orders                 # optionnel pour PURGE
scriptPath: sql/populate.sql  # pour POPULATE
timeout: 60s
```

Outputs stockés : `{ "rowsAffected": 1500, "duration": "2.3s" }`

### FILESYSTEM
```yaml
type: FILESYSTEM
operation: CREATE             # CREATE | DELETE | UPLOAD | CLEANUP
path: /data/input
source: s3://bucket/file.csv  # pour UPLOAD
recursive: true               # pour DELETE/CLEANUP
```

### SHELL
```yaml
type: SHELL
command: "./scripts/prepare.sh"
args:
  - "--env"
  - "prod"
workingDirectory: /app
env:
  MY_VAR: value
timeout: 2m
successExitCodes: [0, 1]      # default [0]
```

Outputs stockés : `{ "exitCode": 0, "stdout": "...", "stderr": "..." }`

### DOCKER
```yaml
type: DOCKER
action: START                 # START | STOP | PULL
image: nginx:latest
containerName: test-nginx
ports:
  - "8080:80"
env:
  NGINX_PORT: "80"
waitForHealthCheck: true
healthCheckTimeout: 30s
```

### KAFKA_CONSUMER
```yaml
type: KAFKA_CONSUMER
operation: MONITOR            # MONITOR | CONSUME | COUNT
topic: customer-events
groupId: perf-monitor
bootstrapServers: kafka:9092  # optionnel si config globale
maxMessages: 1000
timeout: 5m
```

Outputs stockés : `{ "messagesConsumed": 850, "lag": 12 }`

### KAFKA_PRODUCER
```yaml
type: KAFKA_PRODUCER
operation: PRELOAD
topic: input-events
bootstrapServers: kafka:9092
messageCount: 10000
messageTemplate: classpath:kafka/event-template.json
batchSize: 500
```

### MOCK_SERVER
```yaml
type: MOCK_SERVER
deployment: EMBEDDED          # EMBEDDED | EXTERNAL
port: 8090
mappingsPath: wiremock/mappings
action: START                 # START | STOP | RESET | VERIFY
externalUrl: http://mock:8090 # pour deployment: EXTERNAL
```

Outputs stockés : `{ "port": 8090, "url": "http://localhost:8090" }`

### GATLING (voir spec 06 pour le détail)
```yaml
type: GATLING
simulation: com.example.CustomerApiSimulation
loadModel: api-load
```

---

## 4. Enregistrement des Executors

```java
// Chaque executor s'annote et s'enregistre automatiquement
@Preparation(name = "database", description = "DB operations: purge, populate, migrate")
public class DatabaseTaskExecutor implements TaskExecutor {

    @Override
    public String getSupportedTaskName() { return "database"; }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        var operation = (String) step.parameters().get("operation");
        // ...
    }
}
```

Le `TaskExecutorRegistry` est un Spring Bean qui collecte tous les `TaskExecutor`
via injection de collection :

```java
@Component
public class DefaultTaskExecutorRegistry implements TaskExecutorRegistry {
    // Spring injecte tous les beans TaskExecutor via collection
    // Clé de registre : executor.getSupportedTaskName() (String, pas enum)
    public DefaultTaskExecutorRegistry(List<TaskExecutor> executors) {
        executors.forEach(this::register);
    }
}
```

---

## 5. Convention de Stockage dans ExecutionContext

Chaque executor stocke ses résultats dans l'ExecutionContext sous la clé correspondant
au `step.id()`. La structure du store est : `taskId → Map<AgentId, TaskResult>`.

```java
// Convention : clé = step.id(), valeur = résultats par agent
// L'orchestrateur merge les résultats de l'agent dans le store global après réception.
// L'executor retourne ses outputs dans TaskResult.outputs — l'orchestrateur s'occupe du merge.
```

Exemples de clés dans l'ExecutionContext global (côté orchestrateur) :
- `"purge-db"`  → `{ "agent-local": TaskResult(outputs={rowsAffected: 1500}) }`
- `"kafka-monitor"` → `{ "agent-kafka-01": TaskResult(outputs={messagesConsumed: 850}) }`
- `"start-mock"` → `{ "agent-local": TaskResult(outputs={port: 8090, url: "..."}) }`
- `"customer-api-load"` → `{ "agent-perf-01": TaskResult(outputs=InjectionResult) }`

Les assertions accèdent au contexte via `context.getFirst(taskId, type)` ou
`context.getAll(taskId)` (voir spec 02 section 4).

---

## 6. Datasource Configuration (ADR-014)

**Règle** : la config technique JDBC vit dans `application.yaml` sous
`platform.datasources.<nom-logique>`. Le YAML de scénario ne référence qu'un **nom
logique** — jamais de credentials (CNF-03, ADR-006). Les credentials passent par
variables d'environnement.

### 6.1 application.yaml — config technique

```yaml
platform:
  datasources:
    customer-db:
      url: jdbc:postgresql://localhost:5432/customers
      username: ${DB_USER:postgres}
      password: ${DB_PASSWORD:changeme}
      driver-class-name: org.postgresql.Driver
      hikari:
        maximum-pool-size: 10
        minimum-idle: 2
        connection-timeout: 30000
    warehouse-db:
      url: jdbc:postgresql://warehouse:5432/wh
      username: ${WH_USER:postgres}
      password: ${WH_PASSWORD:changeme}
      driver-class-name: org.postgresql.Driver
```

### 6.2 scenario.yaml — référence logique uniquement

```yaml
steps:
  - id: purge-db
    task: database
    phase: PREPARATION
    parameters:
      operation: PURGE
      datasource: customer-db      # ← résolu contre platform.datasources.customer-db
      table: orders
```

### 6.3 Résolution nom logique → DataSource (Sous-option C1)

`@ConfigurationProperties(prefix = "platform")` binde `platform.datasources.*` dans
une `Map<String, DatasourceProperties>`. Un `@Bean DatasourceProvider` construit un
`HikariDataSource` par entrée et l'enregistre par nom. Le `DatabaseTaskExecutor`
injecte `DatasourceProvider` et résout via `datasourceProvider.get(name)`.

- `PlatformDatasourcesProperties` (record `@ConfigurationProperties`)
- `DatasourceConfiguration` (`@Bean DatasourceProvider`, construit les HikariDataSource)
- `DatasourceProvider` : registre logique (n'est plus `@Component` — fourni par le `@Bean`)

Si le scénario référence une datasource absente de `application.yaml` →
`TaskResult.failed("No datasource registered for name: ...")`.

Détail complet et code de référence : `.claude/adr/ADR-014-datasource-configuration.md`.

---

## 7. Système de Plugin — JARs Externes

### 7.1 Configuration

```yaml
platform:
  plugins:
    dir: /plugins      # répertoire scanné au démarrage (défaut : ./plugins)
    enabled: true      # désactiver pour les environnements sans plugins
```

### 7.2 Annotations — Contrat des Plugins

Les trois annotations sont définies dans le module `platform-plugin-api`
(artifact léger fourni aux développeurs de plugins) :

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Preparation {
    String name();                   // clé de résolution DSL → doit être unique dans le registre
    String version() default "1.0.0";
    String description() default "";
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Injection {
    String name();
    String version() default "1.0.0";
    String description() default "";
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Assertion {
    String name();
    String version() default "1.0.0";
    String description() default "";
}
```

### 7.3 Usage — Implémentations Internes

Les composants internes de la plateforme utilisent exactement les mêmes annotations :

```java
@Preparation(name = "database", description = "DB operations: purge, populate, migrate")
public class DatabaseTaskExecutor implements TaskExecutor { ... }

@Preparation(name = "kafka-consumer", description = "Kafka consume/monitor/count")
public class KafkaConsumerTaskExecutor implements TaskExecutor { ... }

@Preparation(name = "mock-server", description = "WireMock embedded/external")
public class MockServerTaskExecutor implements TaskExecutor { ... }

@Injection(name = "gatling", description = "Gatling load injection")
public class GatlingTaskExecutor implements TaskExecutor { ... }

@Assertion(name = "gatling-metric", description = "Gatling metrics: p95, errorRate...")
public class GatlingMetricAssertionExecutor implements TaskExecutor { ... }

@Assertion(name = "database", description = "SQL count/exists assertions")
public class DatabaseAssertionExecutor implements TaskExecutor { ... }
```

### 7.4 Usage — Plugin Externe

```java
// Dans un JAR plugin externe — dépendance uniquement sur platform-plugin-api
@Preparation(name = "my-custom-seeder", version = "2.1.0",
             description = "Seeds the ACME legacy database")
public class AcmeDbSeeder implements TaskExecutor {

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        var connectionString = (String) step.parameters().get("connectionString");
        // ... logique custom
    }
}
```

```yaml
# Scénario — aucune distinction interne/externe dans le YAML
preparation:
  - id: seed-acme
    type: my-custom-seeder     # → résolu par @Preparation(name = "my-custom-seeder")
    connectionString: jdbc:legacy://host:1521/acme
```

### 7.5 Résolution DSL → Implémentation

Le `type` dans le YAML est la clé de lookup dans le registre :

```
DSL type value  →  PluginRegistry.lookup(phase, name)
                     ↓
                   cherche @Preparation(name="X") si phase=PREPARATION
                   cherche @Injection(name="X")   si phase=INJECTION
                   cherche @Assertion(name="X")   si phase=ASSERTION
```

**Règle de collision** : si un plugin externe déclare le même `name` qu'un composant
interne, le plugin externe écrase l'interne. Un warning est loggé :
```
WARN PluginRegistry - Override: @Preparation(name="database") from plugin
     acme-plugin-1.0.jar supersedes internal DatabaseTaskExecutor
```

### 7.6 Transport et Publisher Custom via Properties

Pour les extensions de transport et de publisher, pas d'annotation — configuration
par nom de classe qualifié :

```yaml
# Transport custom (doit implémenter ExecutionTransport)
transport:
  type: CUSTOM
  custom-class: com.acme.MyCustomTransport

# Publisher custom (doit implémenter ReportPublisher)
reporting:
  publishers:
    - target: CUSTOM
      custom-class: com.acme.MyNexusPublisher
      properties:
        url: https://nexus.acme.com
        repo: perf-reports
```

### 7.7 Interface PluginLoader

```java
public interface PluginLoader {
    PluginLoadResult load(Path pluginDirectory);
}

public record PluginLoadResult(
    int jarsLoaded,
    int executorsRegistered,
    List<PluginWarning> warnings,  // collisions, non-instanciables
    List<PluginError> errors       // JARs corrompus, interfaces non implémentées
) {
    public boolean hasErrors() { return !errors.isEmpty(); }
}
```

**Comportement au démarrage** :
- JAR invalide (corrompu, manque de dépendances) → `PluginError` loggé, JAR ignoré
- Collision de `name` → `PluginWarning` loggé, plugin externe gagne
- Classe non instanciable (pas de constructeur no-arg) → `PluginError`, classe ignorée
- Aucun plugin trouvé → démarrage normal, log INFO

### 7.8 Module platform-plugin-api

Module Maven léger fourni aux développeurs de plugins.
Contient **uniquement** :
- Les 3 annotations (`@Preparation`, `@Injection`, `@Assertion`)
- L'interface `TaskExecutor`
- Les records `TaskResult`, `ExecutionContext`, `StepDefinition`, `TaskId`
- L'interface `TaskExecutor` (avec `execute(context, step)` et `getSupportedTaskName()`)
- Note : `TaskType` enum supprimé — le type est le `String taskName` de `StepDefinition`
- Pas de dépendance Spring

```xml
<dependency>
    <groupId>com.performance</groupId>
    <artifactId>platform-plugin-api</artifactId>
    <version>${platform.version}</version>
    <scope>provided</scope>  <!-- provided : disponible au runtime de la plateforme -->
</dependency>
```

---

## 8. Règle Spring-first — Composants à Utiliser (ADR-013)

**Règle ferme (CC-05)** : pour tout composant de `platform-infrastructure`, vérifier
d'abord si Spring/Spring Boot fournit un équivalent configurable. Ne coder custom que
ce que Spring n'offre pas. Ne s'applique **jamais** à `platform-domain` ni
`platform-plugin-api` (0 Spring — CF-08, ADR-004).

| Besoin | À utiliser (Spring) | NE PAS coder à la main |
|---|---|---|
| Exécuter un script SQL | `ResourceDatabasePopulator` | `sql.split(";")` + boucle `Statement.execute` |
| Lire une ressource `classpath:`/`file:` | `DefaultResourceLoader` → `Resource` | `getResourceAsStream` + `Files.readString` |
| Pool de connexions JDBC | `HikariDataSource` | pool maison, `DriverManager` ad hoc |
| Binding YAML → objet | `@ConfigurationProperties` (records) | parsing manuel de `Map<String,Object>` |
| SQL simple (COUNT/EXISTS) | `JdbcTemplate` / `JdbcClient` | `Connection` + `PreparedStatement` manuels |
| Transaction | `TransactionTemplate` / `@Transactional` | `setAutoCommit`/`commit`/`rollback` manuels |

**Dépendances `platform-infrastructure`** : `spring-jdbc` + `HikariCP` (cf. ADR-013).

> Note POPULATE : `ResourceDatabasePopulator` n'expose pas de `rowsAffected`. L'output
> de POPULATE devient `scriptExecuted` (et non `rowsAffected`). PURGE conserve
> `rowsAffected`. Détail : `.claude/adr/ADR-013-spring-first-infrastructure.md`.

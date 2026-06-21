# PDR-022 — HTTP Target Registry + HttpClientTaskExecutor

**Module Maven** : `platform-infrastructure`
**Package** : `com.performance.platform.infrastructure.executor.http`
**Statut** : WAITING
**Specs de référence** : `.claude/specifications/03-task-framework.md`, `.claude/adr/ADR-013-spring-first-infrastructure.md`, `.claude/adr/ADR-014-datasource-configuration.md`
**Dépend de** : PDR-010 DONE (DatasourceProvider pattern à répliquer), PDR-020 DONE (KafkaClusterRegistry — même pattern)
**Issues** : ISSUE-092, ISSUE-093, ISSUE-094, ISSUE-095

---

## Responsabilité

Introduit un registre nommé de cibles HTTP (`HttpTargetRegistry`) calqué sur `DatasourceProvider` / `KafkaClusterRegistry`. Chaque target HTTP est défini dans `application-*.yaml` sous `platform.http-targets.<id>.*` et référencé dans le scénario DSL uniquement par son identifiant logique.

Ajoute la résolution de noms logiques de chemins : `platform.http-targets.<id>.paths.<logical-name>` → chemin réel. Permet le versioning d'API sans changer les scénarios (`path: submit-event` → `/api/v1/events` sur dev, `/api/v2/events` sur prod).

Crée un nouvel executor `HttpClientTaskExecutor` (`@Preparation name="http-client"`) pour les appels HTTP arbitraires dans les phases de préparation (reset état SUT, health check, trigger batch, seeding via API REST, lecture statut WireMock...).

Refactorise `MockServerTaskExecutor` et `HttpMockAssertionExecutor` pour utiliser le même pattern de référence (`target: wiremock` au lieu d'une URL inline).

**Ne fait PAS** : ne touche pas à `HttpExecutionTransport` (transport interne orchestrateur↔agents, différent concern).

---

## Interfaces Publiques

```java
// ---- Properties (binding Spring @ConfigurationProperties) ----

/**
 * Propriétés d'une cible HTTP nommée.
 * Déclarées sous platform.http-targets.<id>.*
 *
 * Exemple application.yaml :
 *   platform:
 *     http-targets:
 *       wiremock:
 *         base-url: ${WIREMOCK_URL:http://wiremock:8080}
 *         connection-timeout: 2s
 *         read-timeout: 5s
 *         default-headers:
 *           Content-Type: application/json
 *       device-api:
 *         base-url: ${DEVICE_API_URL:http://device-api:8082}
 *         read-timeout: 10s
 *         paths:
 *           submit-event: /api/v1/events
 *           check-device: /api/v1/devices
 */
public record HttpTargetProperties(
    String baseUrl,                           // required — URL de base sans trailing slash
    Duration connectionTimeout,               // default 2s
    Duration readTimeout,                     // default 5s
    Map<String, String> defaultHeaders,       // headers ajoutés à chaque requête (ex: Auth)
    Map<String, String> paths                 // logical-path → actual-path (env-specific)
) {
    public HttpTargetProperties {
        Objects.requireNonNull(baseUrl, "baseUrl required");
        connectionTimeout = connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(2);
        readTimeout       = readTimeout       != null ? readTimeout       : Duration.ofSeconds(5);
        defaultHeaders    = defaultHeaders    != null ? Map.copyOf(defaultHeaders) : Map.of();
        paths             = paths             != null ? Map.copyOf(paths)           : Map.of();
    }
}

/**
 * Binding racine. Clé : platform.http-targets.*
 */
@ConfigurationProperties(prefix = "platform")
public record PlatformHttpTargetsProperties(
    Map<String, HttpTargetProperties> httpTargets
) {
    public PlatformHttpTargetsProperties {
        httpTargets = httpTargets != null ? Map.copyOf(httpTargets) : Map.of();
    }
}

// ---- Registry ----

/**
 * Registre thread-safe des cibles HTTP nommées.
 * Instancié par HttpTargetConfiguration (Spring @Bean).
 * Pattern identique à DatasourceProvider et KafkaClusterRegistry.
 */
public class HttpTargetRegistry {

    /**
     * Retourne les propriétés du target nommé, ou null si inconnu.
     */
    public HttpTargetProperties get(String targetName);

    /**
     * Retourne le RestClient Spring pré-configuré pour ce target
     * (base-url, timeouts, default-headers déjà appliqués).
     * Crée le client si première demande (lazy).
     */
    public RestClient clientFor(String targetName);

    /**
     * Résout un nom logique de chemin en chemin réel pour un target donné.
     * Fallback : retourne logicalPath tel quel si aucun mapping trouvé.
     *
     * Exemple :
     *   resolvePath("device-api", "submit-event") → "/api/v1/events"  (depuis paths map)
     *   resolvePath("device-api", "/direct/path") → "/direct/path"    (fallback)
     */
    public String resolvePath(String targetName, String logicalPath);
}

// ---- Configuration Spring ----

@Configuration
@EnableConfigurationProperties(PlatformHttpTargetsProperties.class)
public class HttpTargetConfiguration {

    @Bean
    public HttpTargetRegistry httpTargetRegistry(
            PlatformHttpTargetsProperties props,
            RestClient.Builder restClientBuilder);
}

// ---- Nouvel Executor ----

/**
 * TaskExecutor pour appels HTTP arbitraires dans les phases de préparation.
 *
 * Paramètres DSL :
 *   target          : nom logique (platform.http-targets.*) — required
 *   method          : GET | POST | PUT | DELETE | PATCH        — default GET
 *   path            : chemin logique ou absolu                 — required
 *   body            : corps de la requête (String JSON)        — optional
 *   expectedStatus  : code HTTP attendu (assertion souple)     — optional (0 = pas d'assertion)
 *
 * Outputs : { statusCode: 200, responseBody: "...", durationMs: 42 }
 *
 * Usage typique dans scenario.yaml :
 *   - name: reset-wiremock
 *     type: preparation
 *     task: http-client
 *     parameters:
 *       target: wiremock
 *       method: DELETE
 *       path: /__admin/requests
 *       expectedStatus: 200
 */
@Preparation(name = "http-client", version = "1.0.0",
        description = "HTTP client for test preparation: health checks, resets, API seeding")
@Component
public class HttpClientTaskExecutor implements TaskExecutor {
    // Injecte HttpTargetRegistry
    // Utilise RestClient Spring (Virtual Thread friendly)
    // Toute I/O bloquante sous Virtual Thread
}
```

---

## Règles de Comportement

- **Résolution path** : `resolvePath(target, logical)` cherche dans `paths` map. Absent → retourne `logical` (fallback, rétrocompatible avec chemins absolus).
- **RestClient** : créé une fois par target (lazy init dans `HttpTargetRegistry`). `RestClient.Builder` injecté par Spring (auto-configuré). Timeouts appliqués via `ClientHttpRequestFactory`.
- **`expectedStatus`** : si défini et != 0, le TaskResult est `failed` si le code HTTP reçu ne correspond pas. Aucune exception levée — retourner `TaskResult.failed(...)`.
- **Virtual Threads** : l'appel HTTP bloquant est exécuté dans un Virtual Thread (`Executors.newVirtualThreadPerTaskExecutor()`).
- **`MockServerTaskExecutor`** refactorisé : le paramètre `wiremockUrl` inline devient `target: <nom>`. Rétrocompatibilité : si `wiremockUrl` est fourni sans `target`, utiliser l'URL directement (log WARN de dépréciation).
- **`HttpMockAssertionExecutor`** refactorisé : même règle — `target:` remplace `wiremockUrl` inline.
- **Sécurité** : `defaultHeaders` permet d'injecter `Authorization: Bearer ${TOKEN}` sans l'exposer dans le scénario.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-010 (DatasourceProvider)       → pattern identique
  PDR-020 (KafkaClusterRegistry)     → même pattern, même structure
  spring-web (RestClient)            → déjà dans Spring Boot 4.x
  platform-infrastructure/pom.xml    → RestClient disponible via spring-boot-starter-web

Ce PDR est utilisé par :
  PDR-024 (scénarios exemples)       → scenarios YAML utilisent target: wiremock, path: /__admin/requests
```

---

## Critères de Done (PDR complet)

- [ ] ISSUE-092, 093, 094, 095 toutes DONE
- [ ] `HttpTargetRegistry`, `HttpClientTaskExecutor` dans interfaces-registry.md statut STABLE
- [ ] `MockServerTaskExecutor` et `HttpMockAssertionExecutor` : aucune URL inline — `target:` reference uniquement (avec fallback déprécié)
- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Aucun `new URL(...)`, aucun `HttpURLConnection` raw dans les executors HTTP

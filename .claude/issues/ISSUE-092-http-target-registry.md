# ISSUE-092 — HttpTargetRegistry + HttpTargetProperties + HttpTargetConfiguration

**PDR** : PDR-022
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : aucune (parallélisable avec ISSUE-086)
**Estime** : M

---

## Objectif

Créer l'infrastructure de configuration nommée pour les cibles HTTP, en suivant exactement le pattern `DatasourceProvider` / `KafkaClusterRegistry` (ADR-014 étendu).

Le Developer produit : `HttpTargetProperties`, `PlatformHttpTargetsProperties`, `HttpTargetRegistry`, `HttpTargetConfiguration` + mise à jour de `application-orchestrator.yaml`.

À la fin : on peut (1) binder des targets depuis un yaml, (2) résoudre un chemin logique, (3) obtenir un `RestClient` Spring pré-configuré pour un target donné.

---

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/http/
  ├── HttpTargetProperties.java         — record + Map<String,String> paths
  ├── PlatformHttpTargetsProperties.java — @ConfigurationProperties(prefix="platform")
  ├── HttpTargetRegistry.java           — registre + resolvePath() + clientFor()
  └── HttpTargetConfiguration.java      — @Configuration @EnableConfigurationProperties

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/http/
  └── HttpTargetRegistryTest.java       — tests unitaires (pas d'HTTP réel)

platform-app/src/main/resources/application-orchestrator.yaml  — ajouter bloc platform.http-targets.*
platform-app/src/main/resources/application-local.yaml         — ajouter bloc platform.http-targets.*
```

---

## Interfaces à Implémenter

```java
// HttpTargetProperties.java
public record HttpTargetProperties(
    String baseUrl,
    Duration connectionTimeout,
    Duration readTimeout,
    Map<String, String> defaultHeaders,
    Map<String, String> paths
) {
    public HttpTargetProperties {
        Objects.requireNonNull(baseUrl, "baseUrl required");
        connectionTimeout = connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(2);
        readTimeout       = readTimeout       != null ? readTimeout       : Duration.ofSeconds(5);
        defaultHeaders    = defaultHeaders    != null ? Map.copyOf(defaultHeaders) : Map.of();
        paths             = paths             != null ? Map.copyOf(paths)           : Map.of();
    }
}

// PlatformHttpTargetsProperties.java
@ConfigurationProperties(prefix = "platform")
public record PlatformHttpTargetsProperties(
    Map<String, HttpTargetProperties> httpTargets
) {
    public PlatformHttpTargetsProperties {
        httpTargets = httpTargets != null ? Map.copyOf(httpTargets) : Map.of();
    }
}

// HttpTargetRegistry.java — PAS un @Component
public class HttpTargetRegistry {

    private final Map<String, HttpTargetProperties> targets;
    private final RestClient.Builder restClientBuilder;
    // Cache lazy des RestClient instanciés par target
    private final ConcurrentMap<String, RestClient> clientCache = new ConcurrentHashMap<>();

    public HttpTargetRegistry(Map<String, HttpTargetProperties> targets,
                               RestClient.Builder restClientBuilder) {
        this.targets = Map.copyOf(targets);
        this.restClientBuilder = Objects.requireNonNull(restClientBuilder);
    }

    /** Retourne les propriétés du target ou null si inconnu. */
    public HttpTargetProperties get(String targetName) { ... }

    /**
     * Retourne un RestClient Spring pré-configuré (baseUrl, timeouts, headers).
     * Lazy-init, thread-safe via ConcurrentHashMap.computeIfAbsent.
     */
    public RestClient clientFor(String targetName) {
        HttpTargetProperties props = get(targetName);
        if (props == null) throw new IllegalArgumentException("Unknown http-target: " + targetName);
        return clientCache.computeIfAbsent(targetName, k -> buildClient(props));
    }

    /**
     * Résout un nom logique de chemin vers le chemin réel.
     * Fallback : retourne logicalPath tel quel si aucun mapping trouvé.
     */
    public String resolvePath(String targetName, String logicalPath) {
        HttpTargetProperties props = get(targetName);
        if (props == null) return logicalPath;
        return props.paths().getOrDefault(logicalPath, logicalPath);
    }

    private RestClient buildClient(HttpTargetProperties props) {
        // restClientBuilder.baseUrl(props.baseUrl())
        //   + requestFactory avec connectTimeout + readTimeout
        //   + defaultHeaders depuis props.defaultHeaders()
        return restClientBuilder.clone()
                .baseUrl(props.baseUrl())
                // .requestFactory(...)  → JdkClientHttpRequestFactory avec timeouts
                .defaultHeaders(h -> props.defaultHeaders().forEach(h::set))
                .build();
    }
}

// HttpTargetConfiguration.java
@Configuration
@EnableConfigurationProperties(PlatformHttpTargetsProperties.class)
public class HttpTargetConfiguration {

    @Bean
    public HttpTargetRegistry httpTargetRegistry(
            PlatformHttpTargetsProperties props,
            RestClient.Builder restClientBuilder) {
        return new HttpTargetRegistry(props.httpTargets(), restClientBuilder);
    }
}
```

**Ajout `application-orchestrator.yaml`** :
```yaml
platform:
  # ... (datasources + kafka-clusters existants)
  http-targets:
    wiremock:
      base-url: ${WIREMOCK_URL:http://wiremock:8080}
      connection-timeout: 2s
      read-timeout: 5s
      paths:
        reset-requests: /__admin/requests
        count-requests: /__admin/requests/count
        health: /__admin/health
    device-api:
      base-url: ${DEVICE_API_URL:http://device-api:8082}
      connection-timeout: 2s
      read-timeout: 10s
      paths:
        submit-event: /api/events
        check-device: /api/devices
```

**Ajout `application-local.yaml`** :
```yaml
platform:
  http-targets:
    wiremock:
      base-url: ${WIREMOCK_URL:http://localhost:8090}
      paths:
        reset-requests: /__admin/requests
        count-requests: /__admin/requests/count
    device-api:
      base-url: ${DEVICE_API_URL:http://localhost:8082}
      paths:
        submit-event: /api/events
```

---

## Règles Spécifiques

- `RestClient.Builder` est auto-configuré par Spring Boot — l'injecter sans le recréer.
- Timeouts : utiliser `JdkClientHttpRequestFactory` (Java 21+ HttpClient) avec `connectTimeout` et `readTimeout` depuis les properties. Spring Boot 4.x fournit `JdkClientHttpRequestFactory` nativement.
- **Tests** : `HttpTargetRegistryTest` teste uniquement le binding + `resolvePath` + `clientFor` (pas de requête HTTP réelle). Mock `RestClient.Builder` pour éviter les appels réseau.

---

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Test : binding depuis yaml → `HttpTargetRegistry.get("wiremock")` → non null
- [ ] Test : `resolvePath("wiremock", "reset-requests")` → `"/__admin/requests"`
- [ ] Test : `resolvePath("wiremock", "/direct")` → `"/direct"` (fallback)
- [ ] Test : `clientFor("device-api")` → `RestClient` non null (lazy init)
- [ ] Test : `clientFor("unknown")` → `IllegalArgumentException`
- [ ] `application-orchestrator.yaml` et `application-local.yaml` contiennent `platform.http-targets.*`
- [ ] `.claude/progress.md` mis à jour : ISSUE-092 → DONE
- [ ] `.claude/context/interfaces-registry.md` : HttpTargetRegistry → IN PROGRESS

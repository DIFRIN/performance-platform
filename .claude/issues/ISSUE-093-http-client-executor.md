# ISSUE-093 — HttpClientTaskExecutor (@Preparation http-client)

**PDR** : PDR-022
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-092 (HttpTargetRegistry doit être DONE)
**Estime** : M

---

## Objectif

Créer un nouveau `TaskExecutor` annoté `@Preparation(name="http-client")` permettant d'effectuer des appels HTTP arbitraires dans les phases de préparation d'un scénario (reset état SUT, health check, trigger batch, seeding via API, vérification statut WireMock...).

Le Developer produit `HttpClientTaskExecutor` + ses tests unitaires. L'executor utilise `HttpTargetRegistry` pour résoudre le target et le chemin, puis exécute la requête via `RestClient` Spring.

---

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/http/
  └── HttpClientTaskExecutor.java        — @Preparation, implémente TaskExecutor

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/http/
  └── HttpClientTaskExecutorTest.java    — tests unitaires (mock RestClient)
```

---

## Interfaces à Implémenter

```java
/**
 * TaskExecutor pour appels HTTP arbitraires en phase de préparation.
 *
 * Paramètres DSL du step :
 *   target          (required) : nom logique → platform.http-targets.*
 *   method          (optional) : GET | POST | PUT | DELETE | PATCH — default GET
 *   path            (required) : chemin logique (résolu via paths map) ou absolu
 *   body            (optional) : corps JSON (String) — ignoré si GET/DELETE
 *   expectedStatus  (optional) : code HTTP attendu — 0 = pas d'assertion — default 0
 *   contentType     (optional) : Content-Type — default "application/json"
 *
 * Outputs : { statusCode: 200, responseBody: "...", durationMs: 42 }
 *
 * Usage typique :
 *   - name: reset-wiremock
 *     type: preparation
 *     task: http-client
 *     parameters:
 *       target: wiremock
 *       method: DELETE
 *       path: reset-requests       # → /__admin/requests via resolvePath
 *       expectedStatus: 200
 *
 *   - name: check-api-health
 *     task: http-client
 *     parameters:
 *       target: device-api
 *       method: GET
 *       path: /actuator/health
 *       expectedStatus: 200
 */
@Preparation(name = "http-client", version = "1.0.0",
        description = "HTTP client for test preparation: health checks, resets, API seeding")
@Component
public class HttpClientTaskExecutor implements TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpClientTaskExecutor.class);

    // Output keys
    static final String OUTPUT_STATUS_CODE  = "statusCode";
    static final String OUTPUT_RESPONSE_BODY = "responseBody";
    static final String OUTPUT_DURATION_MS  = "durationMs";

    private final HttpTargetRegistry targetRegistry;

    public HttpClientTaskExecutor(HttpTargetRegistry targetRegistry) {
        this.targetRegistry = Objects.requireNonNull(targetRegistry, "targetRegistry required");
    }

    @Override
    public String getSupportedTaskName() { return "http-client"; }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context required");
        Objects.requireNonNull(step, "step required");

        long startNanos = System.nanoTime();
        ExecutionId executionId = context.executionId();

        // 1. Extraire paramètres
        String targetName    = (String) step.parameters().get("target");
        String method        = Objects.toString(step.parameters().get("method"), "GET").toUpperCase();
        String logicalPath   = Objects.toString(step.parameters().get("path"), null);
        String body          = (String) step.parameters().get("body");
        int expectedStatus   = parseExpectedStatus(step);
        String contentType   = Objects.toString(step.parameters().get("contentType"), "application/json");

        // 2. Validation
        if (targetName == null || targetName.isBlank())
            return fail(step, startNanos, "Required parameter 'target' is missing");
        if (logicalPath == null || logicalPath.isBlank())
            return fail(step, startNanos, "Required parameter 'path' is missing");

        // 3. Résoudre path (logical → actual)
        String resolvedPath = targetRegistry.resolvePath(targetName, logicalPath);
        RestClient client;
        try {
            client = targetRegistry.clientFor(targetName);
        } catch (IllegalArgumentException e) {
            return fail(step, startNanos, "Unknown http-target: " + targetName);
        }

        // 4. Exécuter en Virtual Thread
        long timeoutMs = step.timeout() != null ? step.timeout().toMillis() : 30_000L;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return executor.submit(() -> executeRequest(
                    step, startNanos, client, method, resolvedPath,
                    body, contentType, expectedStatus, executionId, timeoutMs))
                    .get(timeoutMs + 5_000, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return fail(step, startNanos, "HTTP request timed out after " + timeoutMs + "ms");
        } catch (Exception e) {
            return fail(step, startNanos, "Unexpected error: " + e.getMessage());
        }
    }

    private TaskResult executeRequest(StepDefinition step, long startNanos,
                                      RestClient client, String method, String path,
                                      String body, String contentType, int expectedStatus,
                                      ExecutionId executionId, long timeoutMs) {
        try {
            log.info("action=http_request method={} path={} target={} executionId={}",
                    method, path, step.parameters().get("target"), executionId.value());

            RestClient.ResponseSpec response = switch (method) {
                case "GET"    -> client.get().uri(path).retrieve();
                case "DELETE" -> client.delete().uri(path).retrieve();
                case "POST"   -> client.post().uri(path)
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(body != null ? body : "").retrieve();
                case "PUT"    -> client.put().uri(path)
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(body != null ? body : "").retrieve();
                default -> throw new IllegalArgumentException("Unsupported method: " + method);
            };

            // Lire le status et le body
            // Note: RestClient lève HttpClientErrorException sur 4xx/5xx par défaut
            // → onStatus() pour capturer sans lever exception
            String[] responseBody = {""};
            int[] statusCode = {0};

            response.onStatus(status -> true, (req, res) -> {
                statusCode[0] = res.getStatusCode().value();
                // lire le body (peut être vide)
            });
            // Alternative : utiliser toEntity() pour capturer status + body

            ResponseEntity<String> entity = switch (method) {
                case "GET"    -> client.get().uri(path).retrieve().toEntity(String.class);
                case "DELETE" -> client.delete().uri(path).retrieve().toEntity(String.class);
                case "POST"   -> client.post().uri(path)
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(body != null ? body : "").retrieve().toEntity(String.class);
                case "PUT"    -> client.put().uri(path)
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(body != null ? body : "").retrieve().toEntity(String.class);
                default -> throw new IllegalArgumentException("Unsupported method: " + method);
            };

            int status = entity.getStatusCode().value();
            String respBody = entity.getBody() != null ? entity.getBody() : "";
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            log.info("action=http_response statusCode={} durationMs={} executionId={}",
                    status, elapsed.toMillis(), executionId.value());

            Map<String, Object> outputs = Map.of(
                    OUTPUT_STATUS_CODE,   status,
                    OUTPUT_RESPONSE_BODY, respBody,
                    OUTPUT_DURATION_MS,   elapsed.toMillis()
            );

            // Assertion sur le status code si expectedStatus défini
            if (expectedStatus != 0 && status != expectedStatus) {
                return TaskResult.failed(step.id(), getSupportedTaskName(), elapsed,
                        "Expected HTTP " + expectedStatus + " but got " + status, null);
            }

            return TaskResult.success(step.id(), getSupportedTaskName(), elapsed, outputs);

        } catch (Exception e) {
            log.error("action=http_request_failed method={} path={} executionId={}",
                    method, path, executionId.value(), e);
            return fail(step, startNanos, "HTTP request failed: " + e.getMessage());
        }
    }

    private int parseExpectedStatus(StepDefinition step) {
        Object val = step.parameters().get("expectedStatus");
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private TaskResult fail(StepDefinition step, long startNanos, String message) {
        return TaskResult.failed(step.id(), getSupportedTaskName(),
                Duration.ofNanos(System.nanoTime() - startNanos), message, null);
    }
}
```

---

## Règles Spécifiques

- **`RestClient` est thread-safe** : pas besoin de le recréer par exécution. `targetRegistry.clientFor()` retourne toujours le même client (lazy cache).
- **Status non-2xx** : par défaut `RestClient` lève `HttpClientErrorException` sur 4xx et `HttpServerErrorException` sur 5xx. Utiliser `.toEntity(String.class)` sans `.onStatus()` personnalisé → Spring ne lève pas d'exception, retourne le `ResponseEntity` avec le status réel. Le Developer peut vérifier `RestClient.ResponseSpec` javadoc sur ce point.
- **`expectedStatus = 0`** : pas d'assertion → toujours `TaskResult.success` (même si 4xx).
- **Virtual Thread** : le `.get()` bloquant sur le Future est dans un Virtual Thread → pas de blocage de carrier thread.

---

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Test : `target:` manquant → `TaskResult.failed` avec message clair
- [ ] Test : `path:` manquant → `TaskResult.failed` avec message clair
- [ ] Test : GET sur target connu → outputs `{statusCode, responseBody, durationMs}` présents
- [ ] Test : `expectedStatus: 200` + réponse 404 → `TaskResult.failed`
- [ ] Test : `expectedStatus: 0` + réponse 404 → `TaskResult.success`
- [ ] Test : path logique résolu via `resolvePath()` avant la requête
- [ ] Test : target inconnu → `TaskResult.failed` "Unknown http-target"
- [ ] `.claude/progress.md` mis à jour : ISSUE-093 → DONE

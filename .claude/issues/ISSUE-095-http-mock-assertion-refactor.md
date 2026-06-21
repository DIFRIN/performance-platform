# ISSUE-095 — Refactor HttpMockAssertionExecutor → target reference

**PDR** : PDR-022
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-092 (HttpTargetRegistry doit être DONE)
**Estime** : S

---

## Objectif

Refactoriser `HttpMockAssertionExecutor` pour utiliser `HttpTargetRegistry` au lieu d'une URL WireMock inline dans les paramètres du step.

Avant : `parameters.wiremockUrl: "http://wiremock:8080"`
Après : `parameters.target: wiremock` → `HttpTargetRegistry.clientFor("wiremock")`

Rétrocompatibilité : si `wiremockUrl` est fourni sans `target`, utiliser l'URL directement (log WARN).

---

## Fichiers à Modifier

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/http/
  └── HttpMockAssertionExecutor.java     — injecter HttpTargetRegistry (si dans .http package)

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/http/
  └── HttpMockAssertionExecutorTest.java — adapter tests
```

> Note : vérifier le package exact de `HttpMockAssertionExecutor` dans le code existant.

---

## Changements Attendus

```java
// APRÈS
@Assertion(name = "http-mock", version = "2.0.0",
        description = "Assert WireMock received expected requests")
@Component
public class HttpMockAssertionExecutor implements AssertionExecutor {

    private final HttpTargetRegistry targetRegistry;

    public HttpMockAssertionExecutor(HttpTargetRegistry targetRegistry) {
        this.targetRegistry = Objects.requireNonNull(targetRegistry);
    }

    @Override
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
        String targetName  = (String) step.parameters().get("target");
        String wiremockUrl = (String) step.parameters().get("wiremockUrl"); // legacy

        RestClient client;
        if (targetName != null && !targetName.isBlank()) {
            client = targetRegistry.clientFor(targetName);
        } else if (wiremockUrl != null && !wiremockUrl.isBlank()) {
            log.warn("action=deprecated_param param=wiremockUrl — use 'target:'");
            client = RestClient.create(wiremockUrl);
        } else {
            return AssertionResult.failed("Required parameter 'target' is missing");
        }

        // Appel WireMock admin API via client (inchangé fonctionnellement)
        // GET /__admin/requests/count?urlPattern=...
        // ou GET /__admin/requests + filtrer côté client
    }
}
```

**Paramètre DSL after** :
```yaml
# Dans scenario.yaml
- name: assert-iot-commands-received
  type: assertion
  task: http-mock
  parameters:
    target: wiremock              # référence platform.http-targets.wiremock
    urlPattern: "/command"
    expectedRequestCount: 1000
    operator: GREATER_THAN_OR_EQUAL
```

---

## Règles Spécifiques

- Même pattern que ISSUE-094 — même logique de fallback.
- Si `HttpMockAssertionExecutor` se trouve dans un package différent de `.executor.http`, le Developer l'adapte selon le package existant (ne pas déplacer le fichier).
- Version annotation : passer à `version = "2.0.0"` pour signaler le changement d'API.

---

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Test : `target: wiremock` → `HttpTargetRegistry.clientFor("wiremock")` appelé
- [ ] Test : `wiremockUrl:` (legacy) → log WARN + client éphémère
- [ ] Tests WireMock existants (fonctionnels) toujours verts
- [ ] `.claude/progress.md` mis à jour : ISSUE-095 → DONE, PDR-022 → DONE (si 092..095 DONE)
- [ ] `.claude/context/interfaces-registry.md` : HttpTargetRegistry, HttpClientTaskExecutor → STABLE

# ISSUE-094 — Refactor MockServerTaskExecutor → target reference

**PDR** : PDR-022
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-092 (HttpTargetRegistry doit être DONE)
**Estime** : S

---

## Objectif

Refactoriser `MockServerTaskExecutor` pour utiliser `HttpTargetRegistry` au lieu d'une URL WireMock inline dans les paramètres du step.

Avant : `parameters.wiremockUrl: "http://wiremock:8080"`
Après : `parameters.target: wiremock` → `HttpTargetRegistry.clientFor("wiremock")`

Rétrocompatibilité : si `wiremockUrl` est fourni sans `target`, utiliser l'URL directement (log WARN "déprécié").

---

## Fichiers à Modifier

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/mock/
  └── MockServerTaskExecutor.java        — injecter HttpTargetRegistry, adapter la résolution URL

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/mock/
  └── MockServerTaskExecutorTest.java    — adapter les tests
```

---

## Changements Attendus

```java
// AVANT
@Component
public class MockServerTaskExecutor implements TaskExecutor {

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        String wiremockUrl = (String) step.parameters().get("wiremockUrl"); // inline URL
        // → utilise wiremockUrl directement
    }
}

// APRÈS
@Component
public class MockServerTaskExecutor implements TaskExecutor {

    private final HttpTargetRegistry targetRegistry;

    public MockServerTaskExecutor(HttpTargetRegistry targetRegistry) {
        this.targetRegistry = Objects.requireNonNull(targetRegistry);
    }

    @Override
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        String targetName  = (String) step.parameters().get("target");
        String wiremockUrl = (String) step.parameters().get("wiremockUrl"); // legacy

        RestClient client;
        if (targetName != null && !targetName.isBlank()) {
            client = targetRegistry.clientFor(targetName);
        } else if (wiremockUrl != null && !wiremockUrl.isBlank()) {
            log.warn("action=deprecated_param param=wiremockUrl stepId={} — use 'target:' instead",
                    step.id().value());
            // Créer un client éphémère avec l'URL inline
            client = RestClient.create(wiremockUrl);
        } else {
            return fail(step, startNanos, "Required parameter 'target' (or legacy 'wiremockUrl') is missing");
        }
        // ... reste inchangé
    }
}
```

**Paramètre DSL after** :
```yaml
# APRÈS (recommandé)
parameters:
  target: wiremock
  operation: ADD_STUB
  stub: '{"request":{"method":"POST","url":"/command"},"response":{"status":200}}'

# Legacy (déprécié, log WARN)
parameters:
  wiremockUrl: "http://wiremock:8080"
  operation: ADD_STUB
```

---

## Règles Spécifiques

- Ne pas supprimer le support `wiremockUrl` inline — le rétrocompatibilité est maintenue avec log WARN.
- Tests existants : adapter ceux qui passaient `wiremockUrl:` pour passer `target:` (ou les garder pour valider la rétrocompatibilité — les deux sont valides).
- Injecter `HttpTargetRegistry` par constructeur (pas `@Autowired` field).

---

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Test : `target: wiremock` → `HttpTargetRegistry.clientFor("wiremock")` appelé
- [ ] Test : `wiremockUrl: http://...` (legacy) → log WARN émis, client éphémère créé
- [ ] Test : ni `target:` ni `wiremockUrl:` → `TaskResult.failed`
- [ ] `.claude/progress.md` mis à jour : ISSUE-094 → DONE

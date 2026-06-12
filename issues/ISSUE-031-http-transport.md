# ISSUE-031 — HttpExecutionTransport (broadcast vers agents enregistrés)

**PDR** : PDR-008
**Module** : `platform-transport`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-027, ISSUE-028, ISSUE-013
**Estime** : L

---

## Objectif

Implémenter `HttpExecutionTransport` : l'orchestrateur POST la `TaskExecutionRequest` à tous
les agents enregistrés supportant le taskName (`broadcastMode=ALL_CAPABLE`). Callbacks events
via endpoint.

## Fichiers à Créer

```
platform-transport/src/main/java/com/performance/platform/transport/http/
  ├── HttpExecutionTransport.java
  └── HttpEventCallbackController.java   — reçoit les events des agents (POST /api/v1/events)

platform-transport/src/test/java/com/performance/platform/transport/http/
  └── HttpExecutionTransportTest.java
```

## Interfaces à Implémenter

```java
@Component
@ConditionalOnProperty(name = "transport.type", havingValue = "HTTP")
public class HttpExecutionTransport implements ExecutionTransport {
    public HttpExecutionTransport(HttpTransportProperties props, AgentRegistryPort registry) { /* ... */ }
    public TransportType getType() { return TransportType.HTTP; }
}
```

## Règles Spécifiques

- `dispatchTask` : `registry.findByTaskName(taskName)` → POST à chaque `httpCallbackUrl`.
- `broadcastMode=ALL_CAPABLE` → tous les agents capables (multi-claim) ; `FIRST_AVAILABLE` → premier seulement.
- Agent répond 202 Accepted ; events publiés via POST sur `callbackBasePath`.
- Client HTTP sous Virtual Threads (`HttpClient` JDK).

## Critères de Done

- [ ] `mvn test -pl platform-transport -q` → 0 erreur
- [ ] dispatch envoie à tous les agents capables (mock registry + serveur HTTP de test)
- [ ] `getType()` == HTTP
- [ ] `progress.md` mis à jour : ISSUE-031 → DONE
- [ ] `context/interfaces-registry.md` : `HttpExecutionTransport` → STABLE

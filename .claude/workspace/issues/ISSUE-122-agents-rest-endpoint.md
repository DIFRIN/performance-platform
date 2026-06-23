# ISSUE-122: Endpoint REST GET /api/v1/agents (ORCHESTRATOR)

**PDR** : PDR-027
**Module** : `platform-app`
**Statut** : APPROVED
**Priorité** : P2
**Bloquée par** : ISSUE-121
**Taille** : S
**Estime** : S

---

## Objectif

Exposer `GET /api/v1/agents` (conditionnel ORCHESTRATOR) qui wire `AgentRegistryPort.findAll()` et
mappe chaque `AgentDescriptor` vers `AgentResponse`. Le Developer peut verifier via MockMvc que la liste
des agents (id, etat, supportedTasks, lastHeartbeat) est retournee en mode ORCHESTRATOR.

---

## Fichiers à Créer / Modifier

```
platform-app/src/main/java/com/performance/platform/app/api/
  └── AgentController.java              — GET /api/v1/agents (conditionnel ORCHESTRATOR)

platform-app/src/main/java/com/performance/platform/app/api/dto/
  └── AgentResponse.java

platform-app/src/test/java/com/performance/platform/app/api/
  └── AgentControllerTest.java          — @WebMvcTest
```

---

## Interfaces à Implémenter

```java
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "runtime", name = "role", havingValue = "ORCHESTRATOR")
public class AgentController {
    @GetMapping("/agents")               // → List<AgentResponse>
}

public record AgentResponse(
    String agentId, String name, String state,
    java.util.Set<String> supportedTasks, String lastHeartbeatAt) {}
```

---

## Règles Spécifiques

- **CONFIRME ARCHITECT** : la cle canonique est `runtime.role=ORCHESTRATOR`
  (cf. `RuntimeModeResolver.PROP_RUNTIME_ROLE`, `application-orchestrator.yaml`, et le bridge env
  `MODE`→`runtime.role` de `RuntimeConfigEnvironmentPostProcessor`). Donc
  `@ConditionalOnProperty(prefix = "runtime", name = "role", havingValue = "ORCHESTRATOR")` est CORRECT.
  En LOCAL, `runtime.role` vaut `NONE` (non defini) → condition false → controller absent. Ne PAS inventer `runtime.mode=ORCHESTRATOR` (le mode ne prend que LOCAL/DISTRIBUTED).
- Wire `AgentRegistryPort.findAll()` (deja existant — pas de nouvelle methode de port).
- Mapper `AgentDescriptor` → `AgentResponse` : `id().value()`, `name()`, `state().name()`, `supportedTaskNames()`, `lastHeartbeatAt()` (ISO-8601 ; non-null cote domaine).
- En LOCAL : le controller n'est pas monte (condition false) — pas d'erreur, juste absent.

---

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur, 0 warning
- [ ] `GET /agents` (ORCHESTRATOR) → liste mappee depuis findAll()
- [ ] Controller absent en mode LOCAL (condition)
- [ ] `.claude/workspace/progress.md` : ISSUE-122 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (AgentResponse)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel

# PDR-018 — Application Assembly (Bootstrap & Config)

**Module Maven** : `platform-app`
**Package** : `com.performance.platform.app`
**Statut** : WAITING
**Specs de référence** : `.claude/architecture.md` §1, §5, `.claude/specifications/09-deployment.md` §1, ADR-005, ADR-006, `.claude/constraints.md` CF-01
**Dépend de** : PDR-005, PDR-006, PDR-008, PDR-009, PDR-010, PDR-011, PDR-012, PDR-013, PDR-014, PDR-015, PDR-016, PDR-017
**Issues** : ISSUE-077, ISSUE-078, ISSUE-079, ISSUE-080, ISSUE-081, ISSUE-082

---

## Responsabilité

Point d'entrée Spring Boot unique (artefact unique, CF-01). Assemble tous les modules,
résout le mode (LOCAL/DISTRIBUTED) et le rôle (ORCHESTRATOR/AGENT) selon la priorité
env var > yaml (ADR-006), expose l'API REST (submit scénario, status, cancel), bootstrap
le `PluginLoader` au démarrage, et fournit les 3 fichiers de config (local/orchestrator/agent).

---

## Interfaces Publiques

```java
@SpringBootApplication
@Modulith
public class PerformancePlatformApplication {
    public static void main(String[] args) { SpringApplication.run(...); }
}

// Résolution du mode/rôle selon priorité env var > property (ADR-006)
@Component
public class RuntimeModeResolver {
    public RuntimeMode resolveMode();   // RUNTIME_MODE env > runtime.mode yaml
    public RuntimeRole resolveRole();   // MODE env > runtime.role yaml
}

public enum RuntimeRole { ORCHESTRATOR, AGENT, NONE }

@RestController
@RequestMapping("/api/v1")
public class ScenarioController {
    @PostMapping("/scenarios")          // submit YAML → ExecutionId
    @GetMapping("/executions/{id}")     // status
    @PostMapping("/executions/{id}/cancel")
    @GetMapping("/executions/{id}/report")
}

// Bootstrap des plugins au démarrage
@Component
public class PluginBootstrap {
    public PluginBootstrap(PluginLoader loader) { /* ApplicationRunner → loader.load(dir) */ }
}
```

---

## Règles de Comportement

- Un seul JAR, une seule classe main (CF-01).
- Priorité de config (ADR-006) : `RUNTIME_MODE` env > `runtime.mode` yaml ; `MODE` env > `runtime.role` yaml ; `TRANSPORT_TYPE` env > `transport.type` yaml. **Env var gagne toujours.**
- `@ConditionalOnProperty` câblé via les properties résolues (env var réécrite en property au boot si présente).
- API REST sécurisée OAuth2/JWT (CNF-03).
- `PluginBootstrap` exécuté une fois au démarrage (CF-06) ; JAR invalide ne crash pas.
- 3 fichiers : `application-local.yaml`, `application-orchestrator.yaml`, `application-agent.yaml`.
- Health checks `/actuator/health/{readiness,liveness}` (CD-02).
- Validation du scénario à la soumission (CF-05) : refuser un scénario invalide avec erreurs détaillées.

---

## Dépendances Techniques

```
Ce PDR utilise : TOUS les modules fonctionnels (engine, transport, agent, infra,
                 gatling, assertion, reporting, observability, scenario-dsl).

Ce PDR est utilisé par :
  PDR-019 (deployment) → l'image Docker package ce JAR.
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] `mvn clean install` → JAR exécutable unique
- [ ] Test : env var prioritaire sur property (ADR-006)
- [ ] Test E2E LOCAL : submit YAML → exécution → rapport généré
- [ ] `@Modulith` verifies() passe (pas de dépendance inter-module directe)

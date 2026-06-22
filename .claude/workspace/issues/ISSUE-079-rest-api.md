# ISSUE-079 — API REST (submit / status / cancel / report)

**PDR** : PDR-018
**Module** : `platform-app`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-077, ISSUE-018
**Estime** : M

---

## Objectif

Implémenter le `ScenarioController` REST exposant la soumission de scénario, le status,
l'annulation et la récupération du rapport.

## Fichiers à Créer

```
platform-app/src/main/java/com/performance/platform/app/api/
  ├── ScenarioController.java
  ├── dto/SubmitResponse.java
  ├── dto/ExecutionStatusResponse.java
  └── ApiExceptionHandler.java   — @RestControllerAdvice (validation → 400 détaillé)

platform-app/src/test/java/com/performance/platform/app/api/
  └── ScenarioControllerTest.java — @WebMvcTest
```

## Interfaces à Implémenter

```java
@RestController
@RequestMapping("/api/v1")
public class ScenarioController {
    @PostMapping("/scenarios")               // body = YAML → 202 + ExecutionId
    @GetMapping("/executions/{id}")          // status
    @PostMapping("/executions/{id}/cancel")  // 202
    @GetMapping("/executions/{id}/report")   // rapport généré
}
```

## Règles Spécifiques

- Soumission : valider le scénario (CF-05) — invalide → 400 avec erreurs détaillées (champ + message).
- Délègue aux use cases : `ExecuteScenarioUseCase`, `GetExecutionStatusUseCase`, `CancelExecutionUseCase`, `GenerateReportUseCase`.
- `ScenarioValidationException` → 400 ; `NoAvailableAgentException` → 503.

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur
- [ ] POST scénario valide → 202 + ExecutionId ; invalide → 400 détaillé
- [ ] GET status retourne l'état
- [ ] `.claude/progress.md` mis à jour : ISSUE-079 → DONE
- [ ] `.claude/context/interfaces-registry.md` mis à jour

# ISSUE-124: Endpoint POST /api/v1/scenarios/upload + DTO d'erreur de validation structure

**PDR** : PDR-027
**Module** : `platform-app`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-121
**Taille** : M
**Estime** : M

---

## Objectif

Exposer `POST /api/v1/scenarios/upload` acceptant un fichier multipart OU un contenu YAML texte, validant
le scenario puis l'executant immediatement. Aligner le DTO d'erreur de validation sur un format structure
(liste champ + message). Couvrir tous les nouveaux endpoints PDR-027 par des tests MockMvc.

---

## Fichiers à Créer / Modifier

```
platform-app/src/main/java/com/performance/platform/app/api/
  ├── ScenarioUploadController.java     — POST /scenarios/upload (multipart + textarea YAML)
  └── ApiExceptionHandler.java          — MODIF : ScenarioValidationException → ValidationErrorResponse

platform-app/src/main/java/com/performance/platform/app/api/dto/
  └── ValidationErrorResponse.java      — { message, errors[] { field, message } }

platform-app/src/test/java/com/performance/platform/app/api/
  └── ScenarioUploadControllerTest.java — @WebMvcTest (multipart, textarea, 400 field-level, 202)
```

---

## Interfaces à Implémenter

```java
@RestController
@RequestMapping("/api/v1")
public class ScenarioUploadController {
    // multipart file OU champ form "yaml" ; valide ; execute immediatement
    @PostMapping(value = "/scenarios/upload")
    ResponseEntity<?> upload(@RequestParam(required = false) MultipartFile file,
                             @RequestParam(required = false) String yaml);
}

public record ValidationErrorResponse(String message, List<FieldError> errors) {
    public record FieldError(String field, String message) {}
}
```

---

## Règles Spécifiques

- Accepte soit `file` (multipart), soit `yaml` (champ texte). Si les deux absents → 400.
- Valide via `ScenarioParsingUseCase` + validation existante. Invalide → `400` + `ValidationErrorResponse` (champ + message).
- Valide → execute immediatement via `ExecuteScenarioUseCase`, retourne `202` + `{executionId}`.
- Pas de catalogue : execution immediate (aucun stockage de scenario pour usage ulterieur).
- **ATTENTION ARCHITECT (interface publique existante)** : `ApiExceptionHandler` gere DEJA
  `ScenarioValidationException` → 400 avec le payload `{error, message, details:[{field, message, path}]}`
  (consomme par `ScenarioControllerTest`). Introduire un NOUVEAU format `ValidationErrorResponse`
  `{message, errors:[{field, message}]}` est un CHANGEMENT BREAKING du contrat d'erreur existant.
  Choix impose : **NE PAS casser le handler existant**. Deux options acceptables :
  (a) le nouveau DTO `ValidationErrorResponse` reutilise les MEMES cles que le payload actuel
  (`error` + `message` + `errors[]` aligne sur `details[]`), de sorte que la reponse reste retro-compatible ;
  OU (b) `ValidationErrorResponse` est un type structure interne mappe vers le MEME JSON que celui produit
  aujourd'hui. Mettre a jour `ScenarioControllerTest` uniquement si la forme JSON est strictement preservee.
  Toute divergence de forme JSON = ADR + accord Architect prealable.
- `ApiExceptionHandler` : `ScenarioValidationException` → 400 (format ci-dessus) ; `NoAvailableAgentException` → 503 ;
  `ExecutionNotDeletableException` (ADR-020) → 409 ; execution introuvable → 404.
- I/O fichier en Virtual Threads si bloquant.

---

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur, 0 warning
- [ ] Upload multipart valide → 202 + executionId
- [ ] Upload textarea YAML valide → 202 + executionId
- [ ] Scenario invalide → 400 avec erreurs field-level (champ + message)
- [ ] Tous les nouveaux endpoints PDR-027 couverts par MockMvc
- [ ] `.claude/workspace/progress.md` : ISSUE-124 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (ValidationErrorResponse, ScenarioUploadController)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel

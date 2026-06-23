# ISSUE-123: Endpoint REST GET /api/v1/executions/{id}/report (stream fichier deja genere)

**PDR** : PDR-027
**Module** : `platform-app`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-121
**Taille** : M
**Estime** : M

---

## Objectif

Exposer `GET /api/v1/executions/{id}/report?format=html|pdf|json` qui STREAME le rapport DEJA genere
(en fin d'execution, lifecycle) depuis le repertoire de sortie de `ReportFileWriter`. Le Developer peut
verifier que le bon Content-Type est applique, que 404 est retourne si le fichier n'existe pas encore,
et que la generation n'est JAMAIS declenchee par cet endpoint.

---

## Fichiers à Créer / Modifier

```
platform-app/src/main/java/com/performance/platform/app/api/
  └── ReportController.java             — GET /executions/{id}/report?format= (stream)

platform-app/src/test/java/com/performance/platform/app/api/
  └── ReportControllerTest.java         — @WebMvcTest (html/pdf/json + 404 absent)
```

---

## Interfaces à Implémenter

```java
@RestController
@RequestMapping("/api/v1")
public class ReportController {
    // ?format=html|pdf|json ; streame le fichier deja genere ; 404 si absent
    @GetMapping("/executions/{id}/report")
    ResponseEntity<byte[]> getReport(@PathVariable String id,
                                     @RequestParam(defaultValue = "html") String format);
}
```

---

## Règles Spécifiques

- Le controller NE declenche JAMAIS la generation (elle est automatique en fin d'execution — PDR-015).
- **PRECISION ARCHITECT** : NE PAS dependre de `ReportFileWriter` (son `outputDirectory` est prive).
  Injecter le bean `ReportProperties` (`@ConfigurationProperties("reporting")`, `platform-reporting`) et
  resoudre le chemin selon le layout reel de `ReportFileWriter` :
  `{reporting.output-directory}/{executionId}/campaign.{html|pdf|json}` (defaut output-directory = `reports`).
  Utiliser `org.springframework.core.io.Resource` / `Files` (Spring-first, ADR-013/CC-05) — pas de
  construction de chemin fragile. `platform-app` depend deja de `platform-reporting` (dependance legitime).
- `Content-Type` selon `format` : `text/html`, `application/pdf`, `application/json`.
- Fichier absent (rapport pas encore genere) → `404 Not Found` (l'IHM poll).
- `format` invalide → `400`.
- Lecture du fichier en Virtual Threads si I/O bloquant (regle plateforme).
- Validation du `executionId`/`format` pour eviter tout path traversal (resoudre puis verifier que le
  chemin reste sous `output-directory`).

---

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur, 0 warning
- [ ] `?format=html|pdf|json` → bytes avec bon Content-Type
- [ ] Fichier absent → 404 ; format invalide → 400
- [ ] Aucune generation declenchee par le controller (verifie : pas d'appel a GenerateReportUseCase ici)
- [ ] `.claude/workspace/progress.md` : ISSUE-123 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (ReportController)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel

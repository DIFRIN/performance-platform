# ISSUE-130: Vue rapport (poll → iframe HTML + download PDF/JSON) + E2E Testcontainers

**PDR** : PDR-029
**Module** : `platform-app`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-129
**Taille** : L
**Estime** : L

---

## Objectif

Implementer la vue rapport : poll de disponibilite du rapport, affichage HTML inline en iframe des disponibilite,
boutons de telechargement PDF/JSON. Ajouter un test E2E Testcontainers couvrant les nouveaux endpoints
(list, tasks, delete, agents, report, upload). Le Developer peut verifier que la vue passe de "en cours"
a "affiche" sans jamais declencher la generation, et que l'E2E valide la chaine complete.

---

## Fichiers à Créer / Modifier

```
platform-app/src/main/resources/static/assets/js/views/
  └── report.js                         — vue rapport (poll dispo → iframe HTML + download PDF/JSON)

platform-app/src/main/resources/static/assets/js/
  └── api.js                            — MODIF : reportUrl(id, format), checkReportAvailable(id)

platform-app/src/test/java/com/performance/platform/e2e/
  └── WebUiApiE2ETest.java              — Testcontainers : list/tasks/delete/agents/report/upload
```

---

## Interfaces à Implémenter

```javascript
// api.js
export function reportUrl(id, format) { return `/api/v1/executions/${id}/report?format=${format}`; }
export async function isReportAvailable(id) { /* HEAD/GET html → 200 vs 404 */ }

// views/report.js
export function mount(el, executionId) {
    // poll 3s jusqu'a dispo; puis iframe src=reportUrl(html); boutons download pdf/json; return cleanup()
}
```

---

## Règles Spécifiques

- Le client NE declenche JAMAIS la generation (rapport genere automatiquement en fin d'execution).
- Poll `GET .../report?format=html` : tant que `404`, afficher "rapport en cours de generation" ; des `200`,
  charger l'HTML dans une `<iframe>` et activer les boutons de telechargement PDF/JSON.
- Polling 3s avec cleanup (meme contrat que les autres vues).
- **E2E Testcontainers** (PostgreSQL) : demarrer l'app en mode permettant l'IHM/API, soumettre un scenario,
  puis verifier : `GET /executions` (liste + progress), `GET /executions/{id}/tasks`, `GET /agents` (selon mode),
  `GET /executions/{id}/report` (404 puis 200 apres generation), `DELETE /executions/{id}` (204),
  `POST /scenarios/upload` (202 + 400 invalide). Tag `integration-tests`.

---

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur, 0 warning
- [ ] `mvn verify -P integration-tests` → E2E vert
- [ ] Vue rapport : poll → iframe HTML inline + boutons download PDF/JSON, jamais de generation declenchee
- [ ] E2E couvre list/tasks/delete/agents/report/upload (incl. 404→200 rapport et 400 upload invalide)
- [ ] Polling 3s avec cleanup
- [ ] `.claude/workspace/progress.md` : ISSUE-130 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (report.js, WebUiApiE2ETest)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel

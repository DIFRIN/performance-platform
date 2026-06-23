# PDR-029 — IHM Frontend Views (vanilla JS)

**Module Maven** : `platform-app`
**Package** : `src/main/resources/static` (assets), `src/test/java/com/performance/platform/e2e` (E2E)
**Statut** : WAITING
**Specs de reference** : Design valide utilisateur (Web IHM)
**Depend de** : PDR-028 (IHM Web Serving & Static Shell)
**Issues** : ISSUE-127, ISSUE-128, ISSUE-129, ISSUE-130

---

## Responsabilite

Ce PDR implemente les vues fonctionnelles de l'IHM en vanilla JS, branchees sur le shell et le routeur
de PDR-028 et consommant l'API de PDR-027. Toutes les vues sont en HTML/CSS + JS pur, polling client
toutes les 3 secondes pour le temps reel (pas de SSE/WebSocket en v1).

**Vues livrees** :
1. **Liste des executions** ("liste des scenarios") : polling 3s, filtre de statut (actives vs terminees), actions cancel/delete.
2. **Detail d'execution** : steps/tasks par phase, statut ok/ko/running, messages d'erreur, barre de progression
   (`{total, ok, ko, running}` calcule cote serveur), decoupage par phase. Liste de tasks paginee/resumee, detail a la demande.
3. **Dashboard agents** (ORCHESTRATOR uniquement) : id agent, etat, supportedTasks, lastHeartbeat.
4. **Upload de scenario** : fichier multipart + textarea YAML, execution immediate, erreurs de validation field-level inline.
5. **Vue rapport** : poll de disponibilite → affichage HTML inline (iframe) + telechargement PDF/JSON.

Plus un test E2E Testcontainers couvrant les nouveaux endpoints.

**Ce que ce PDR NE fait PAS (hors scope v1)** :
- Editeur YAML in-browser, graphes de tendance, graphes de metriques Gatling live.
- Actions de gestion d'agent (drain/stop), gestion utilisateurs/roles.
- i18n/theming, suppression de rapport.
- Generation de rapport a la demande (le rapport est genere automatiquement en fin d'execution — PDR-015/PDR-027).
- SSE/WebSocket (polling 3s uniquement).

---

## Interfaces Publiques

> Cette couche est du JavaScript vanilla, pas de signature Java publique.
> Les "interfaces" sont les contrats d'appel API (consommes, definis en PDR-027) et la structure des assets.

### Structure des assets statiques

```
platform-app/src/main/resources/static/
  ├── index.html                  (shell — livre en PDR-028)
  ├── assets/
  │   ├── css/app.css             (layout — livre en PDR-028, etendu ici)
  │   ├── js/router.js            (routeur a hash — livre en PDR-028)
  │   ├── js/api.js               (client fetch vers /api/v1/** — livre ici)
  │   ├── js/views/executions.js  (vue liste)
  │   ├── js/views/execution-detail.js
  │   ├── js/views/agents.js
  │   ├── js/views/upload.js
  │   └── js/views/report.js
```

### Contrats API consommes (definis en PDR-027)

```
GET    /api/v1/executions?limit=N           → ExecutionSummaryResponse[]  (avec progress)
GET    /api/v1/executions/{id}              → ExecutionStatusResponse     (avec progress)
GET    /api/v1/executions/{id}/tasks        → TaskListResponse
DELETE /api/v1/executions/{id}              → 204
POST   /api/v1/executions/{id}/cancel       → 202
GET    /api/v1/agents                       → AgentResponse[]   (ORCHESTRATOR)
POST   /api/v1/scenarios/upload             → 202 {executionId} | 400 ValidationErrorResponse
GET    /api/v1/executions/{id}/report?format=html|pdf|json → bytes | 404
```

---

## Regles de Comportement

- **Polling 3s** : un intervalle unique par vue active, demarre au montage de la vue, ARRETE au demontage
  (changement de route). Pas de fuite d'intervalle. Le polling rafraichit la liste, le detail, ou la disponibilite rapport.
- **Filtre de statut** : actives = `STARTED | RUNNING` ; terminees = `COMPLETED | FAILED | CANCELLED`. Filtre cote client
  sur les donnees deja recues.
- **Barre de progression** : alimentee EXCLUSIVEMENT par le champ `progress {total, ok, ko, running}` du serveur.
  Aucun calcul cote client.
- **Tasks** : affichage resume par defaut (taskName, phase, statut). Detail complet (errorMessage, timings) a la demande
  via expansion de ligne. Liste paginee/resumee cote client si volumineuse.
- **Dashboard agents** : la vue n'est accessible/visible qu'en mode ORCHESTRATOR. En LOCAL, masquer l'entree de nav
  (ou afficher un etat vide explicite si l'endpoint renvoie vide).
- **Upload** : deux modes d'entree (fichier multipart OU textarea YAML). A la soumission, si `400`, afficher les erreurs
  `ValidationErrorResponse` inline a cote des champs concernes (champ + message). Si `202`, rediriger vers le detail d'execution.
- **Rapport** : poller `GET .../report?format=html` ; tant que `404`, afficher "rapport en cours de generation".
  Des disponibilite, afficher l'HTML dans une `<iframe>` et exposer les boutons de telechargement PDF/JSON.
  Le client NE declenche JAMAIS la generation (elle est automatique en fin d'execution).
- **Cancel / delete** : confirmer avant `DELETE` ; rafraichir la liste apres action.
- **Zero dependance build** : pas d'import npm, pas de framework. `fetch` natif, DOM API natif.

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-028 (IHM Web Serving)        → index.html shell, router.js, app.css, static handler
  PDR-027 (IHM Backend API)        → tous les endpoints /api/v1/** consommes
  Testcontainers (PostgreSQL)      → test E2E couvrant les nouveaux endpoints

Ce PDR est utilise par :
  (feuille — utilisateur final)
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues ISSUE-127 a ISSUE-130 sont DONE
- [ ] Les 5 vues sont fonctionnelles et branchees sur le routeur a hash
- [ ] Polling 3s sans fuite d'intervalle (demarrage au montage, arret au demontage)
- [ ] Barre de progression alimentee par le champ serveur `progress` uniquement
- [ ] Erreurs de validation upload affichees field-level inline
- [ ] Vue rapport : poll → iframe HTML inline + telechargement PDF/JSON ; jamais de declenchement de generation
- [ ] Dashboard agents visible en ORCHESTRATOR uniquement
- [ ] Test E2E Testcontainers couvrant les nouveaux endpoints passe (`mvn verify -P integration-tests`)

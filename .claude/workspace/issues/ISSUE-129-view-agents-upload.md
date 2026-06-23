# ISSUE-129: Vue dashboard agents (ORCHESTRATOR) + vue upload (validation inline)

**PDR** : PDR-029
**Module** : `platform-app`
**Statut** : APPROVED
**Priorité** : P2
**Bloquée par** : ISSUE-128
**Taille** : M
**Estime** : M

---

## Objectif

Implementer la vue dashboard agents (id, etat, supportedTasks, lastHeartbeat — ORCHESTRATOR uniquement)
et la vue upload de scenario (fichier multipart + textarea YAML, execution immediate, erreurs de validation
field-level inline). Le Developer peut verifier que les agents s'affichent en ORCHESTRATOR et que l'upload
montre les erreurs champ par champ ou redirige vers le detail en cas de succes.

---

## Fichiers à Créer / Modifier

```
platform-app/src/main/resources/static/assets/js/views/
  ├── agents.js                         — vue dashboard agents (polling 3s, ORCHESTRATOR)
  └── upload.js                         — vue upload (file + textarea, erreurs inline)

platform-app/src/main/resources/static/assets/js/
  └── api.js                            — MODIF : listAgents(), uploadScenario(fileOrYaml)

platform-app/src/main/resources/static/assets/css/
  └── app.css                           — MODIF : table agents, formulaire upload, messages d'erreur inline
```

---

## Interfaces à Implémenter

```javascript
// api.js
export async function listAgents() { /* GET /api/v1/agents */ }
export async function uploadScenario({ file, yaml }) {
    // POST multipart /api/v1/scenarios/upload ; 202 → {executionId} ; 400 → ValidationErrorResponse
}

// views/agents.js / views/upload.js
export function mount(el) { /* render; poll si agents; return cleanup() */ }
```

---

## Règles Spécifiques

- **Agents** : polling 3s avec cleanup. Colonnes : agentId, state, supportedTasks, lastHeartbeat. Vue accessible/visible
  uniquement en ORCHESTRATOR (lien nav masque sinon ; si endpoint renvoie vide, etat vide explicite).
- **Upload** : deux entrees (fichier multipart OU textarea YAML). A la soumission :
  - `400` → afficher chaque `FieldError {field, message}` de `ValidationErrorResponse` inline a cote du champ concerne.
  - `202` → rediriger vers `/#/executions/{executionId}`.
- Execution immediate (pas de catalogue). Pas de declenchement de rapport (automatique en fin d'execution).

---

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur, 0 warning
- [ ] Dashboard agents affiche id/etat/supportedTasks/lastHeartbeat (ORCHESTRATOR), polling avec cleanup
- [ ] Upload accepte fichier multipart ET textarea YAML
- [ ] Erreurs de validation affichees field-level inline (champ + message)
- [ ] Upload valide → redirection vers le detail d'execution
- [ ] `.claude/workspace/progress.md` : ISSUE-129 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (agents.js, upload.js)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel

# ISSUE-128: Vue detail d'execution (tasks ok/ko, barre progression, phases)

**PDR** : PDR-029
**Module** : `platform-app`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-127
**Taille** : M
**Estime** : M

---

## Objectif

Implementer la vue detail d'execution : decoupage par phase, table des steps/tasks avec statut ok/ko/running
et messages d'erreur, barre de progression serveur. Liste de tasks resumee avec detail complet a la demande.
Le Developer peut verifier que le detail se rafraichit (polling 3s) et affiche la progression et les erreurs.

---

## Fichiers à Créer / Modifier

```
platform-app/src/main/resources/static/assets/js/views/
  └── execution-detail.js               — vue detail : mount(el, executionId) → cleanup()

platform-app/src/main/resources/static/assets/js/
  └── api.js                            — MODIF : getStatus(id), getTasks(id)

platform-app/src/main/resources/static/assets/css/
  └── app.css                           — MODIF : table tasks, phase sections, expansion detail
```

---

## Interfaces à Implémenter

```javascript
// api.js
export async function getExecutionStatus(id) { /* GET /api/v1/executions/{id} (avec progress) */ }
export async function getExecutionTasks(id) { /* GET /api/v1/executions/{id}/tasks */ }

// views/execution-detail.js
export function mount(el, executionId) {
    // render phases + tasks table + progress bar; poll 3s; return cleanup()
}
```

---

## Règles Spécifiques

- Polling 3s avec cleanup (meme contrat que ISSUE-127).
- Barre de progression alimentee par `progress {total, ok, ko, running}` du status serveur (aucun calcul client).
- Tasks groupees par phase. Statut visuel ok/ko/running. `errorMessage` affiche si KO.
- Resume par defaut ; detail complet (errorMessage long, timings) via expansion de ligne (a la demande).
- Lien retour vers la liste (`/#/executions`). Lien vers le rapport (`/#/executions/{id}/report`).

---

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur, 0 warning
- [ ] Detail rafraichi toutes les 3s, intervalle nettoye au changement de route
- [ ] Tasks groupees par phase avec statut ok/ko/running et messages d'erreur
- [ ] Barre de progression alimentee par le champ serveur uniquement
- [ ] Detail complet d'une task accessible a la demande (expansion)
- [ ] `.claude/workspace/progress.md` : ISSUE-128 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (execution-detail.js)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel

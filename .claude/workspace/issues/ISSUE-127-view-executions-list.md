# ISSUE-127: Vue liste des executions (polling 3s, filtre statut, cancel/delete)

**PDR** : PDR-029
**Module** : `platform-app`
**Statut** : APPROVED
**Priorité** : P1
**Bloquée par** : ISSUE-126
**Taille** : M
**Estime** : M

---

## Objectif

Implementer la vue liste des executions : client API `fetch`, polling toutes les 3s, filtre de statut
(actives vs terminees), actions cancel et delete. Le Developer peut verifier que la liste se rafraichit,
que le filtre fonctionne, et que cancel/delete agissent puis rafraichissent.

---

## Fichiers à Créer / Modifier

```
platform-app/src/main/resources/static/assets/js/
  ├── api.js                            — client fetch /api/v1/** (list, status, tasks, cancel, delete, agents, upload, report)
  └── views/executions.js               — vue liste : mount(el) → renvoie cleanup()

platform-app/src/main/resources/static/assets/css/
  └── app.css                           — MODIF : styles table executions, badges statut, barre progress
```

---

## Interfaces à Implémenter

```javascript
// api.js
export async function listExecutions(limit = 50) { /* GET /api/v1/executions?limit= */ }
export async function cancelExecution(id) { /* POST /api/v1/executions/{id}/cancel */ }
export async function deleteExecution(id) { /* DELETE /api/v1/executions/{id} */ }

// views/executions.js
export function mount(el) {
    // render table; setInterval 3s; return () => clearInterval(...) // cleanup
}
```

---

## Règles Spécifiques

- Polling : `setInterval` 3000ms au montage, `clearInterval` dans la fonction cleanup retournee (pas de fuite).
- Filtre statut cote client : actives = `STARTED|RUNNING`, terminees = `COMPLETED|FAILED|CANCELLED`.
- Chaque ligne affiche la barre de progression depuis `progress {total, ok, ko, running}` du serveur (aucun calcul client).
- `delete` : confirmation avant appel ; rafraichir apres succes (204).
- `cancel` : appel (202) puis rafraichir.
- Clic sur une ligne → navigation hash vers `/#/executions/{id}`.

---

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur, 0 warning
- [ ] Liste rafraichie toutes les 3s, intervalle nettoye au changement de route
- [ ] Filtre actives/terminees fonctionnel
- [ ] Barre de progression alimentee par le champ serveur uniquement
- [ ] cancel (202) et delete (204, avec confirmation) rafraichissent la liste
- [ ] `.claude/workspace/progress.md` : ISSUE-127 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (api.js, executions.js)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel

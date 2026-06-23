# ISSUE-126: Shell index.html + CSS layout + nav + routeur a hash

**PDR** : PDR-028
**Module** : `platform-app`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-125
**Taille** : M
**Estime** : M

---

## Objectif

Livrer le shell de l'application monopage : `index.html`, le CSS de layout, le composant de navigation,
et le squelette du routeur a hash. Le Developer peut verifier que `/` charge le shell, que la nav rend
les liens de routes, et que le routeur mappe `/#/route` vers une fonction de rendu (vue vide en placeholder).

---

## Fichiers à Créer / Modifier

```
platform-app/src/main/resources/static/
  ├── index.html                        — shell SPA (charge css + js, conteneur #app, #nav)
  └── assets/
      ├── css/app.css                   — layout de base (header, nav, contenu, etats ok/ko/running)
      └── js/
          ├── router.js                 — routeur a hash : registerRoute(path, renderFn), start(), onChange
          └── nav.js                    — composant nav (liens : executions, upload, agents*)
```

---

## Interfaces à Implémenter

```javascript
// router.js — squelette
export function registerRoute(pattern, renderFn) { /* ... */ }
export function startRouter(mountEl) { /* listen hashchange, dispatch, cleanup precedente vue */ }

// nav.js
export function renderNav(navEl, { orchestrator }) { /* liens; "agents" visible si orchestrator */ }
```

---

## Règles Spécifiques

- 100% HTML/CSS + vanilla JS. Aucun build (pas de npm/bundler). `fetch` et DOM natifs.
- Routeur a hash : une seule entree serveur `/`, le client gere `/#/executions`, `/#/executions/{id}`, `/#/upload`, `/#/agents`, `/#/executions/{id}/report`.
- Le routeur doit appeler un hook de cleanup de la vue precedente (pour arreter les pollings — utilise en PDR-029).
- Lien nav "agents" : visible uniquement si ORCHESTRATOR (info exposee par l'API ou un endpoint de meta ; en placeholder ici, masquable).
- Vues = placeholders ici (rendu reel en PDR-029).
- CSS : classes utilitaires pour les statuts (ok/ko/running) reutilisees par les vues.

---

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur, 0 warning (les tests web restent verts)
- [ ] `/` (web.ui.enabled=true) sert index.html avec nav et conteneur #app
- [ ] Routeur a hash dispatche vers des fonctions de rendu et nettoie la vue precedente
- [ ] Aucun fichier de build front (pas de package.json)
- [ ] `.claude/workspace/progress.md` : ISSUE-126 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (assets shell)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel

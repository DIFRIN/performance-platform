# Skill — Token Budget & Chargement de Contexte

> Ce skill est lu par tous les agents. Il définit les règles de consommation
> de contexte pour minimiser les tokens sans perdre en précision.

---

## Principe Fondamental

> **Charger juste ce qui est nécessaire pour l'action immédiate.**
> Pas "au cas où". Pas "pour avoir le contexte général".
> Le contexte général est dans `.claude/workspace/session-state.md` — pas besoin de relire les specs.

---

## Budget par Rôle

### Developer (session standard)
```
Fichiers obligatoires (~250 tokens) :
  .claude/workspace/current-issue.md            ~300 tokens  — specs, signatures, critères, feedbacks

Fichiers conditionnels :
  agents/developer.md       ~200 tokens   — 1ère session ou si incertitude sur les règles
  .claude/knowledge/glossary.md               ~200 tokens   — si un terme est ambigu
  .claude/workspace/interfaces-registry.md  ~300 tokens  — toujours (évite les divergences)
  Une spec (section ciblée)  ~150-300 tokens — section spécifique uniquement

Total typique : 500-800 tokens de contexte par session
À NE PAS charger : session-state.md, progress.md, PDRs, roadmap, ADRs, guides
```

### Reviewer (session standard)
```
Fichier obligatoire :
  .claude/workspace/current-issue.md            ~300 tokens  — specs, signatures, critères

Fichiers conditionnels :
  agents/reviewer.md        ~250 tokens
  .claude/workspace/interfaces-registry.md  ~300 tokens
  Une spec (section interfaces) ~100 tokens  — vérification des signatures

Total typique : 400-700 tokens
À NE PAS charger : session-state.md, progress.md, .claude/skills/, roadmap, PDRs
```

### Tester (session standard)
```
Fichier obligatoire :
  .claude/workspace/current-issue.md            ~300 tokens  — specs, critères de test

Fichiers conditionnels :
  agents/tester.md (section phase) ~150 tokens
  .claude/workspace/interfaces-registry.md   ~300 tokens

Total typique : 450-700 tokens
```

### Architect (session escalade)
```
Fichiers obligatoires :
  agents/architect.md       ~200 tokens
  .claude/knowledge/adr/*.md                  ~200 tokens (tous, compact)
  .claude/knowledge/architecture.md           ~300 tokens

Fichiers conditionnels :
  spec concernée (section précise) ~150 tokens
  .claude/workspace/interfaces-registry.md  ~300 tokens si interfaces impactées

Total typique : 700-1000 tokens
```

---

## Règles de Lecture Partielle des Specs

Les specs sont longues (150-280 lignes). Ne jamais les lire en entier sauf Phase 1.

| Situation | Lire |
|---|---|
| Implémenter une interface | Section "Interfaces" uniquement |
| Comprendre un comportement | Section concernée + exemples YAML |
| Débugger une validation | Section "Règles de Validation" uniquement |
| Vérifier un format de retour | Section "Modèle de Données" uniquement |
| Phase 1 uniquement | Spec 01 complète (parsing = besoin global) |

Indiquer dans `.claude/workspace/current-issue.md` **quelle section** lire,
pas juste le fichier.

---

## Règle de Non-Redite

Si une information est dans `.claude/workspace/current-issue.md` → ne pas aller la chercher dans la spec.
Si une signature est dans `.claude/workspace/interfaces-registry.md` → ne pas aller la chercher dans la spec.
Si une décision est dans `.claude/workspace/decisions-log.md` → ne pas aller la chercher dans les ADRs.

Ces 3 fichiers sont des **caches** des informations les plus utilisées.
Ils éliminent le besoin de re-lire les sources primaires dans 80% des sessions.

---

## Pattern de Session Optimale

```
1. Lire .claude/workspace/session-state.md               → 30 tokens + décision sur "Files to Load"
2. Lire UNIQUEMENT les fichiers listés  → 400-700 tokens
3. Confirmer compréhension en 3 lignes → 0 tokens de contexte supplémentaire
4. Travailler
5. En fin de session :
   - Mettre à jour .claude/workspace/session-state.md     → maintient le cache pour la prochaine session
   - Mettre à jour interfaces-registry  → évite de relire les specs la prochaine fois
   - Mettre à jour decisions-log        → évite de re-décider
```

---

## Ce qui Tue le Budget Token

❌ Lire tous les fichiers "pour avoir le contexte"
❌ Re-lire une spec entière pour retrouver une signature déjà dans interfaces-registry
❌ Charger .claude/guides/ et .claude/knowledge/adr/ à chaque session
❌ Demander un résumé du projet à chaque session au lieu de lire .claude/workspace/session-state.md
❌ Lire feature-summaries en entier au lieu de juste l'index par module

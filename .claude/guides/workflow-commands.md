# Guide — Déroulement Opérationnel

> Commandes exactes, dans l'ordre, du démarrage du projet jusqu'à la boucle quotidienne.
> Copier-coller dans Claude Code ou dans votre terminal selon le contexte.
> Pas de décisions à prendre — suivre la séquence.

---

## PHASE 0 — Setup Initial (une seule fois)

### 0.0 — Setup des clés API

```bash
# Ajouter dans ~/.zshrc ou ~/.bashrc
export ANTHROPIC_API_KEY="sk-ant-..."
export DEEPSEEK_API_KEY="sk-..."   # https://platform.deepseek.com/api_keys
source /path/to/performance-platform/scripts/shell-functions.sh

# Recharger
source ~/.zshrc

# Vérifier
perf-status
```

Les subagents sont dans `.claude/agents/` — pas de config supplémentaire.

### 0.1 — Préparer le repo

```bash
# Cloner / initialiser le repo
git init performance-platform
cd performance-platform

# Copier le contenu du zip ai-project à la racine
unzip performance-platform-ai-project.zip -d .
mv pp-ai/* .
rm -rf pp-ai/

# Vérifier la structure
ls -la
# Attendu : CLAUDE.md  agents/  .claude/adr/  .claude/context/  .claude/guides/  .claude/issues/  .claude/pdr/
#           .claude/progress.md  .claude/prompts/  .claude/session-state.md  .claude/specifications/  .claude/skills/

git add .
git commit -m "chore: init ai-project structure"
```

### 0.2 — Créer les dossiers de travail Java + installer les hooks

```bash
mkdir -p src plugins
echo "# Performance Engineering Platform" > README.md

# Installer les git hooks (une seule fois)
bash scripts/setup-hooks.sh
# → installe scripts/git-hooks/prepare-commit-msg dans .git/hooks/
# → préfixe automatiquement chaque commit avec [ISSUE-XXX]

git add . && git commit -m "chore: init project skeleton"
# → commit message devient : "[ISSUE-XXX] chore: init project skeleton"
#    (si une Issue est IN PROGRESS dans progress.md)
```

---

## PHASE 1 — System Designer (une seule fois avant de coder)

> **But** : transformer les specs en PDRs et Issues dans `.claude/progress.md`.
> Après cette phase, le Developer n'a plus jamais besoin de lire les specs.

### Session 1.A — Lancer le System Designer

```bash
# Session Anthropic — system-designer tourne comme subagent
cd /path/to/performance-platform
claude-design
```

**Dans la session, taper :**

```
@system-designer produis les PDRs et Issues pour ce projet.
2. .claude/glossary.md
3. .claude/architecture.md
4. .claude/constraints.md
5. .claude/specifications/00-overview.md
6. .claude/specifications/01-scenario-dsl.md
7. .claude/specifications/02-execution-engine.md
8. .claude/specifications/03-task-framework.md
9. .claude/specifications/04-agent-runtime.md
10. .claude/specifications/05-transport-layer.md
11. .claude/specifications/06-injection-gatling.md
12. .claude/specifications/07-assertion-framework.md
13. .claude/specifications/08-report-engine.md
14. .claude/specifications/09-deployment.md
15. .claude/adr/*.md
16. .claude/context/interfaces-registry.md

MISSION :
Après lecture, produire d'abord la liste des PDRs proposés (titres + modules uniquement).
STOP — attendre ma validation avant d'écrire les fichiers.
```

**→ Le System Designer produit une liste de PDRs dans le chat.**

### Session 1.B — Valider et déclencher la création

**Si la liste est correcte, dans la session `claude-design` :**
```
OK, procède à la création.
```

**Si ajustements nécessaires :**
```
Ajustement : [décrire la modification]
Puis procède à la création.
```

**→ Le System Designer crée :**
- `.claude/pdr/PDR-001-*.md` ... `.claude/pdr/PDR-NNN-*.md`
- `.claude/issues/ISSUE-001-*.md` ... `.claude/issues/ISSUE-NNN-*.md`
- Met à jour `.claude/progress.md`
- Met à jour `.claude/context/interfaces-registry.md`

### 0.3 — Commit après System Designer

```bash
git add .claude/pdr/ .claude/issues/ .claude/progress.md .claude/context/interfaces-registry.md
git commit -m "feat: system designer — PDRs and Issues created

$(grep -c 'PDR-' progress.md) PDRs, $(grep -c 'ISSUE-' progress.md) Issues
First actionable: $(grep -m1 'WAITING' .claude/issues/*.md 2>/dev/null | head -1 | cut -d/ -f2)"
```

---

## PHASE 2 — Boucle de Développement (répéter pour chaque Issue)

> **Rythme nominal** : 1 session Developer + 1 session Reviewer par Issue.
> Les Issues de taille S peuvent être groupées (2 par session Developer).

---

### Commande de démarrage de journée

**Toujours commencer par vérifier l'état :**

```bash
# Voir les Issues en cours
grep -A1 "IN PROGRESS\|IN REVIEW" .claude/progress.md | grep "ISSUE-"

# Voir la prochaine Issue disponible
grep -B1 "WAITING" .claude/progress.md | grep "ISSUE-" | head -5

# Résumé rapide
echo "IN PROGRESS : $(grep -c 'IN PROGRESS' progress.md)"
echo "IN REVIEW   : $(grep -c 'IN REVIEW' progress.md)"
echo "DONE        : $(grep -c '✅ DONE' progress.md)"
echo "WAITING     : $(grep -c 'WAITING' progress.md)"
```

---

### Session Developer — Nouvelle Issue

```bash
# Session DeepSeek — developer, reviewer, tester disponibles
claude-dev
```

**Dans la session :**
```
@developer
```
> Le subagent lit progress.md, prend la première Issue disponible, confirme et implémente.



**→ Le Developer implémente l'Issue, écrit les tests unitaires, passe l'Issue IN REVIEW.**

### Après la session Developer

```bash
# Vérifier que les tests passent
mvn test -pl <module-de-l-issue> -q

# Commit
git add .
git commit -m "feat(ISSUE-XXX): <titre de l'issue>

- Fichiers créés : [liste]
- Tests : X unitaires ajoutés
- .claude/progress.md : WAITING → IN REVIEW"
```

---

### Session Reviewer

**Dans la session `claude-dev` (déjà ouverte ou nouvelle) :**
```
@reviewer
```
> Le subagent trouve l'Issue IN REVIEW dans progress.md, passe toutes les checklists et produit son rapport.



**→ Trois cas possibles :**

---

#### CAS A — APPROVED ✅

```bash
git add .claude/progress.md .claude/context/interfaces-registry.md
git commit -m "review(ISSUE-XXX): APPROVED

Reviewer: APPROVED au 1er cycle
ISSUE-XXX → DONE
[PDR-YYY → DONE si applicable]"
```

**→ Retourner à "Session Developer — Nouvelle Issue".**

---

#### CAS B — CHANGES_REQUESTED ⚠️

**Dans la session `claude-dev` :**

```
@developer Le Reviewer a demandé des corrections sur ISSUE-XXX.
[coller le rapport de review]
```

```bash
git add .
git commit -m "fix(ISSUE-XXX): apply reviewer corrections

Points corrigés :
- [ARCH-XX] ...
- [CRAFT-XX] ..."
```

**→ Relancer la Session Reviewer avec le prompt Re-Review :**

```
Tu es l'agent Reviewer de la Performance Engineering Platform.
Corrections effectuées par le Developer. Re-review des points bloquants uniquement.

Rapport précédent :
[COLLER LE RAPPORT PRÉCÉDENT]

FICHIERS À LIRE :
1. agents/reviewer.md   — section "Règles de Décision" uniquement
2. .claude/issues/ISSUE-XXX.md  — critères de done
3. .claude/pdr/PDR-XXX.md       — si points ARCH concernaient des interfaces

Vérifier UNIQUEMENT les points BLOQUANTS du rapport précédent.
```

---

#### CAS C — Escalade Architect 🏛️

> Si 2ème CHANGES_REQUESTED ou ambiguïté bloquante.

**Dans la session `claude-design` :**

```
@architect ESCALADE — [description du problème + question précise]
```

```bash
# Si l'Architect produit un ADR
git add .claude/adr/ADR-00X-*.md
git commit -m "arch: ADR-00X — [titre de la décision]"
```

---

### Session Tester

**Dans la session `claude-dev` :**
```
@tester
```
> Le subagent trouve l'Issue DONE récente, définit son plan de test, écrit les tests Testcontainers.

```
Tu es l'agent Tester de la Performance Engineering Platform.

L'Issue suivante vient d'être APPROVED par le Reviewer :
[ISSUE-XXX — titre]

FICHIERS À LIRE :
1. agents/tester.md                 — section "Phase X" uniquement
2. .claude/issues/ISSUE-XXX.md              — composants à tester
3. .claude/pdr/PDR-XXX.md                   — interfaces exactes
4. .claude/progress.md                      — voir quelles Issues sont DONE (éviter doublons)

PROCESSUS :
1. Définis ton plan de test
2. Écris les tests d'intégration Testcontainers
3. Exécute et rapport
4. Mets à jour .claude/feature-summaries/README.md
5. Mets à jour .claude/session-state.md
```

```bash
mvn verify -P integration-tests -pl <module>
git add .
git commit -m "test(ISSUE-XXX): integration tests

Tests : X intégration, Y contrats
Infrastructure : Testcontainers (postgres:15, cp-kafka:7.5)
Résultat : X/X passent"
```

---

## PHASE 3 — Reprise après interruption

> Vous revenez après quelques jours. Vous ne savez plus où vous en êtes.

```bash
# Ouvrir n'importe quelle session et demander l'état
claude-dev
```
**Puis :**

```
Lis .claude/session-state.md et progress.md. Dis-moi quelle Issue est active et quelle est la prochaine action.
```

**→ Selon la réponse, copier le prompt correspondant depuis `.claude/prompts/`.**

---

## Référence Rapide — Quel prompt pour quelle situation ?

| Situation | Session | Dans la session |
|---|---|---|
| Création PDRs + Issues | `claude-design` | `@system-designer produis les PDRs` |
| Décision architecturale | `claude-design` | `@architect [question]` |
| Développement | `claude-dev` | `@developer` |
| Review d'une Issue | `claude-dev` | `@reviewer` |
| Corrections post-review | `claude-dev` | `@developer Le Reviewer a demandé des corrections...` |
| Re-review | `claude-dev` | `@reviewer re-review ISSUE-XXX` |
| Tests d'intégration | `claude-dev` | `@tester` |
| Reprise / état | `claude-dev` | `Lis .claude/session-state.md et progress.md, état actuel ?` |

---

## Commandes Slash Claude Code (dans n'importe quelle session)

```
/next     → Quelle est la prochaine action ? Quelle Issue prendre ?
/done     → Marquer l'Issue IN REVIEW + générer le message de commit
/review   → Déléguer au @reviewer sur l'Issue IN REVIEW
```

---

## Commandes de Santé du Projet

```bash
# État global
echo "=== PROGRESS ===" && \
grep -E "WAITING|IN PROGRESS|IN REVIEW|✅ DONE|BLOCKED" .claude/progress.md | \
  awk '{print $NF}' | sort | uniq -c | sort -rn

# Build global
mvn clean install -q && echo "✅ BUILD OK" || echo "❌ BUILD FAILED"

# Tests
mvn test -q && echo "✅ TESTS OK" || echo "❌ TESTS FAILED"

# Coverage (après Phase 1)
mvn jacoco:report -pl platform-domain && \
  grep -A2 "platform-domain" target/site/jacoco/index.html | grep -o '[0-9]*%' | head -1

# Vérifier qu'aucune annotation Spring n'est dans platform-domain
grep -r "@Component\|@Service\|@Repository\|@Autowired" \
  platform-domain/src/main/java/ && echo "❌ VIOLATION" || echo "✅ DOMAIN CLEAN"

# Plugins disponibles
ls -la plugins/ 2>/dev/null || echo "No plugins loaded"
```

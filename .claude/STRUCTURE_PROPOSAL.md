# Proposition de Restructuration — Juin 2026

> À valider avant toute modification.
> **Révisé** selon feedback: workspace dans `.claude/`, permissions portables, issue-start auto par Developer.

---

## 🎯 Objectifs

1. **Moins de contexte chargé par les agents** — 1 seul fichier à lire pour savoir quoi faire
2. **Scripts, pas context** — les transitions d'état sont faites par des scripts shell, pas par l'IA
3. **Permissions portables** — dans `settings.json` (commit), pas `settings.local.json` (local)
4. **Agents autonomes** — le Developer trouve et démarre sa propre Issue, sans intervention humaine
5. **Compatible subagents** — le workflow dev-loop.js fonctionne avec 0 connaissance préalable
6. **Tout dans `.claude/`** — un framework unique, cohérent, qui voyage avec le git

---

## 1. Structure Fichiers

```
performance-platform/
├── pom.xml
├── CLAUDE.md
├── README.md
├── .env / .env.example
│
├── .claude/                                # LE framework de vibe-coding
│   │
│   ├── settings.json                       # 🔑 hooks + PERMISSIONS (commit → portable)
│   ├── settings.local.json                 # surcharges locales uniquement (gitignored)
│   │
│   ├── agents/                             # définitions d'agents (simplifiées)
│   │   ├── developer.md
│   │   ├── reviewer.md
│   │   ├── architect.md
│   │   ├── system-designer.md
│   │   └── tester.md
│   │
│   ├── knowledge/                          # 🆕 connaissances PERMANENTES
│   │   ├── architecture.md
│   │   ├── glossary.md
│   │   ├── roadmap.md
│   │   ├── constraints.md
│   │   ├── adr/                            # ADR-001 à ADR-018
│   │   ├── specs/                          # était specifications/
│   │   │   ├── 00-overview.md
│   │   │   ├── 01-scenario-dsl.md
│   │   │   └── ...
│   │   └── skills/                         # patterns réutilisables
│   │       ├── precision-patterns.md
│   │       ├── hexagonal-architecture.md
│   │       └── ...
│   │
│   ├── workspace/                          # 🆕 TOUT l'état transitoire (fusion context/ + tracking/)
│   │   ├── current-issue.md               # 🔑 SEUL fichier lu par les agents dev/reviewer
│   │   ├── progress.md                    # MAJ par scripts uniquement (jamais chargé en contexte IA)
│   │   ├── session-state.md               # reprise humaine rapide (agents ne lisent pas)
│   │   ├── recommendations-tracking.md    # feedback reviewer (MAJ par scripts)
│   │   ├── interfaces-registry.md         # interfaces implémentées
│   │   ├── decisions-log.md
│   │   ├── dependency-map.md
│   │   ├── known-issues.md
│   │   ├── current-task.md                # scratchpad (si existant)
│   │   └── issues/                        # Issues créées par System Designer
│   │       └── ISSUE-XXX.md
│   │
│   ├── scripts/                           # scripts (nouveaux + existants)
│   │   ├── issue-start.sh                 # 🆕 TODO→IN_PROGRESS, crée current-issue.md
│   │   ├── issue-finish.sh                # 🆕 IN_PROGRESS→IN_REVIEW
│   │   ├── issue-review.sh               # 🆕 enregistre verdict reviewer
│   │   ├── issue-next.sh                  # 🆕 APPROVED→DONE → start next
│   │   ├── issue-block.sh                 # 🆕 bloque l'Issue
│   │   ├── progress-status.sh             # 🆕 résumé 1 ligne (sans charger progress.md)
│   │   ├── dev-loop.sh                    # modifié
│   │   ├── auto-commit-done.sh            # modifié
│   │   ├── commit-done-issues.sh          # inchangé
│   │   └── ...
│   │
│   ├── workflows/
│   │   └── dev-loop.js                    # modifié
│   │
│   ├── commands/                          # slash commands (inchangé)
│   ├── prompts/                           # prompt templates (inchangé)
│   └── guides/                            # how-to (inchangé)
│
├── platform-domain/                       # modules Maven (inchangés)
├── ... (tous les modules)
└── platform-examples/                     # 🆕 ajouté au pom.xml
```

### Suppressions
- ❌ `tracking/` à la racine → fusionné dans `.claude/workspace/`
- ❌ `src/` vide à la racine
- ❌ `.claude/context/` → fusionné dans `.claude/workspace/`
- ❌ `.claude/specifications/` → `.claude/knowledge/specs/`
- ❌ `.claude/issues/` + `.claude/pdr/` → déplacés dans `.claude/workspace/`
- ❌ `.claude/feature-summaries/` → juste un README, pas utile
- ❌ `.claude/session-state.md` → `.claude/workspace/session-state.md`
- ❌ `.claude/progress.md` → `.claude/workspace/progress.md`

### Pourquoi TOUT dans `.claude/` ?
- Un seul dossier = un seul framework, portable via git
- Les permissions `Write(.claude/**)` et `Bash(bash .claude/scripts/*)` couvrent tout
- Le subagent reçoit `.claude/` comme contexte : tout est accessible
- Cohérence : pas de dispersion entre racine et `.claude/`

---

## 2. Le Pattern `current-issue.md`

### Principe

Les agents **ne lisent qu'un seul fichier** : `.claude/workspace/current-issue.md`.
Tout le reste (état, transitions, tracking) est géré par des scripts shell.

### Format de `current-issue.md`

```markdown
# ISSUE-042: Mock Server Task Executor
**Status**: IN_PROGRESS
**PDR**: PDR-010
**Module**: platform-infrastructure
**Started**: 2026-06-22T14:30+02:00

## Description
Implement a mock server task executor that starts/stops a WireMock server
for integration testing. The executor must implement TaskExecutor and be
annotated with @Preparation.

## Interface Signatures
(extraites du PDR par le script)
```java
public interface TaskExecutor {
    TaskResult execute(ExecutionContext ctx);
    String taskName();
}
```

## Files to Create
- platform-infrastructure/src/main/java/.../executor/mock/MockServerTaskExecutor.java
- platform-infrastructure/src/test/java/.../executor/mock/MockServerTaskExecutorTest.java

## Acceptance Criteria
- [ ] MockServerTaskExecutor annotated with @Preparation
- [ ] WireMock server starts on configurable port
- [ ] TaskResult returned with mock server URL in context
- [ ] Test class with 3+ test methods
- [ ] mvn test -pl platform-infrastructure -q passes

## Reviewer Feedback
<!-- Populated by reviewer via issue-review.sh -->
(None yet)
```

### Pourquoi le contenu de l'Issue est INLINE ?
- 1 seul read au lieu de 2 (current-issue.md + issues/ISSUE-XXX.md)
- Le subagent n'a pas besoin de savoir où sont les Issues
- Les signatures d'interfaces sont déjà extraites (pas besoin de lire le PDR)

---

## 3. Scripts de Transition

### 3.0 Comportement clé : `issue-start.sh` auto-détecte

**Si appelé SANS argument** → trouve automatiquement la prochaine Issue TODO.
**Si appelé AVEC argument** → démarre l'Issue spécifiée.

C'est le **Developer agent** qui appelle `issue-start.sh` (pas l'humain). Comme ça :
- Le flux est 100% autonome
- La toute première itération fonctionne (current-issue.md n'existe pas encore)
- Pas de cas particulier "humain doit lancer le premier"

---

### 3.1 `issue-start.sh [ISSUE_ID]`

```bash
#!/usr/bin/env bash
# Usage: issue-start.sh [ISSUE-XXX]
#   Sans argument : trouve la 1ère TODO → IN_PROGRESS, crée current-issue.md
#   Avec argument  : démarre l'Issue spécifiée
# Action: TODO → IN_PROGRESS dans progress.md, crée current-issue.md avec contenu inline

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$ROOT/workspace"
PROGRESS="$WORKSPACE/progress.md"

# ── Résoudre l'Issue ID ──────────────────────────────────────────────────────
if [[ $# -ge 1 ]]; then
    ISSUE_ID="$1"
else
    # Auto-détection : première TODO dans progress.md
    ISSUE_ID=$(grep -oP '^\| \KISSUE-\d+' "$PROGRESS" \
               | while read id; do
                   grep -cP "^\| ${id} .*\| TODO \|" "$PROGRESS" >/dev/null 2>&1 \
                     && echo "$id" \
                     && break
                 done)
    if [[ -z "$ISSUE_ID" ]]; then
        echo "✅ No TODO issues remaining."
        echo "# No active issue — all work complete" > "$WORKSPACE/current-issue.md"
        exit 0
    fi
fi

ISSUE_FILE="$WORKSPACE/issues/${ISSUE_ID}.md"

if [[ ! -f "$ISSUE_FILE" ]]; then
    echo "❌ Issue file not found: $ISSUE_FILE"
    exit 1
fi

# ── Extraire métadonnées ─────────────────────────────────────────────────────
TITLE=$(grep '^# ' "$ISSUE_FILE" | head -1 | sed 's/^# [A-Z0-9-]*: //')
PDR=$(grep -oP 'PDR-\d+' "$ISSUE_FILE" | head -1)
MODULE=$(grep -oP 'platform-[a-z-]+' "$ISSUE_FILE" | head -1)

# ── Marquer IN_PROGRESS dans progress.md (sed ciblé table Issues) ────────────
sed -i "/^## Issues$/,/^## PDRs$/{s/| ${ISSUE_ID} | .* | TODO |/| ${ISSUE_ID} | ${TITLE} | IN_PROGRESS |/}" "$PROGRESS"

# ── Créer current-issue.md ───────────────────────────────────────────────────
cat > "$WORKSPACE/current-issue.md" << INNEREOF
# ${ISSUE_ID}: ${TITLE}
**Status**: IN_PROGRESS
**PDR**: ${PDR:-UNKNOWN}
**Module**: ${MODULE:-UNKNOWN}
**Started**: $(date -Iminutes)

$(tail -n +2 "$ISSUE_FILE")

## Reviewer Feedback
(None yet)
INNEREOF

echo "✅ ${ISSUE_ID} → IN_PROGRESS | current-issue.md ready"
```

---

### 3.2 `issue-finish.sh`

```bash
#!/usr/bin/env bash
# Usage: issue-finish.sh
# Action: IN_PROGRESS → IN_REVIEW dans progress.md + current-issue.md
# Appelé par : Developer agent (quand l'implémentation est finie)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$ROOT/workspace"
CURRENT="$WORKSPACE/current-issue.md"
PROGRESS="$WORKSPACE/progress.md"

if [[ ! -f "$CURRENT" ]]; then
    echo "❌ No current-issue.md found. Run issue-start.sh first."
    exit 1
fi

ISSUE_ID=$(grep -oP '^# \KISSUE-\d+' "$CURRENT")
TITLE=$(grep '^# ' "$CURRENT" | sed 's/^# [A-Z0-9-]*: //')

# Marquer IN_REVIEW dans progress.md
sed -i "/^## Issues$/,/^## PDRs$/{s/| ${ISSUE_ID} | .* | IN_PROGRESS |/| ${ISSUE_ID} | ${TITLE} | IN_REVIEW |/}" "$PROGRESS"

# Mettre à jour current-issue.md
sed -i 's/\*\*Status\*\*: IN_PROGRESS/**Status**: IN_REVIEW/' "$CURRENT"

# Ajouter entrée dans l'historique (append-only)
echo "| $(date -I) | ${ISSUE_ID} | IN_PROGRESS → IN_REVIEW | Developer finished |" >> "$PROGRESS"

echo "✅ ${ISSUE_ID} → IN_REVIEW"
```

---

### 3.3 `issue-review.sh <VERDICT> [MESSAGE]`

```bash
#!/usr/bin/env bash
# Usage: issue-review.sh APPROVED
#        issue-review.sh CHANGES_REQUESTED "Fix typo in class name"
# Action: met à jour current-issue.md + progress.md selon verdict
# Appelé par : Reviewer agent

set -euo pipefail

VERDICT="${1:?Usage: issue-review.sh <APPROVED|CHANGES_REQUESTED> [message]}"
MESSAGE="${2:-}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$ROOT/workspace"
CURRENT="$WORKSPACE/current-issue.md"
PROGRESS="$WORKSPACE/progress.md"

ISSUE_ID=$(grep -oP '^# \KISSUE-\d+' "$CURRENT")
TITLE=$(grep '^# ' "$CURRENT" | sed 's/^# [A-Z0-9-]*: //')

if [[ "$VERDICT" == "APPROVED" ]]; then
    sed -i "/^## Issues$/,/^## PDRs$/{s/| ${ISSUE_ID} | .* | IN_REVIEW |/| ${ISSUE_ID} | ${TITLE} | APPROVED |/}" "$PROGRESS"
    sed -i 's/\*\*Status\*\*: IN_REVIEW/**Status**: APPROVED/' "$CURRENT"
    echo "| $(date -I) | ${ISSUE_ID} | IN_REVIEW → APPROVED | Reviewer approved |" >> "$PROGRESS"
    echo "✅ ${ISSUE_ID} APPROVED"

elif [[ "$VERDICT" == "CHANGES_REQUESTED" ]]; then
    sed -i "/^## Issues$/,/^## PDRs$/{s/| ${ISSUE_ID} | .* | IN_REVIEW |/| ${ISSUE_ID} | ${TITLE} | CHANGES_REQUESTED |/}" "$PROGRESS"
    sed -i 's/\*\*Status\*\*: IN_REVIEW/**Status**: CHANGES_REQUESTED/' "$CURRENT"

    # Append feedback dans current-issue.md
    cat >> "$CURRENT" << INNEREOF

---
## Reviewer Feedback — $(date -Iminutes)
${MESSAGE}
INNEREOF

    echo "| $(date -I) | ${ISSUE_ID} | IN_REVIEW → CHANGES_REQUESTED | ${MESSAGE} |" >> "$PROGRESS"
    echo "⚠️  ${ISSUE_ID} CHANGES_REQUESTED: ${MESSAGE}"

else
    echo "❌ Invalid verdict: $VERDICT (use APPROVED or CHANGES_REQUESTED)"
    exit 1
fi
```

---

### 3.4 `issue-next.sh`

```bash
#!/usr/bin/env bash
# Usage: issue-next.sh
# Action: APPROVED → DONE, archive current-issue → lance la suivante
# Appelé par : Reviewer agent (après APPROVED + commit)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$ROOT/workspace"
CURRENT="$WORKSPACE/current-issue.md"
PROGRESS="$WORKSPACE/progress.md"
ARCHIVE_DIR="$WORKSPACE/issues/"

ISSUE_ID=$(grep -oP '^# \KISSUE-\d+' "$CURRENT")
TITLE=$(grep '^# ' "$CURRENT" | sed 's/^# [A-Z0-9-]*: //')

# 1. Marquer DONE dans progress.md
sed -i "/^## Issues$/,/^## PDRs$/{s/| ${ISSUE_ID} | .* | APPROVED |/| ${ISSUE_ID} | ${TITLE} | DONE |/}" "$PROGRESS"
echo "| $(date -I) | ${ISSUE_ID} | APPROVED → DONE | issue-next.sh |" >> "$PROGRESS"

# 2. Archiver
cp "$CURRENT" "${ARCHIVE_DIR}${ISSUE_ID}-completed.md"
echo "📦 Archived: ${ARCHIVE_DIR}${ISSUE_ID}-completed.md"

# 3. Lancer la prochaine
exec "$(dirname "${BASH_SOURCE[0]}")/issue-start.sh"
```

---

### 3.5 `progress-status.sh`

```bash
#!/usr/bin/env bash
# Usage: progress-status.sh
# Output 1 ligne: "DONE:42 | APPROVED:1 | REVIEW:2 | PROGRESS:1 | CHANGES:0 | TODO:58 | WAIT:0 | BLOCKED:0"
# Utile pour les agents qui veulent savoir où on en est sans charger progress.md

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROGRESS="$ROOT/workspace/progress.md"

for status in DONE APPROVED IN_REVIEW IN_PROGRESS CHANGES_REQUESTED TODO WAITING BLOCKED; do
    count=$(grep -cP "^\| ISSUE-\d+ \| .* \| ${status} \|" "$PROGRESS" 2>/dev/null || echo 0)
    printf "${status}:%s " "$count"
done
echo
```

---

## 4. Nouveau Protocole Agent

### Developer Agent (`agents/developer.md`)

```markdown
## Protocole (1 fichier + 2 scripts)

### Démarrage
1. Vérifier si `.claude/workspace/current-issue.md` existe :
   - **NON** → `bash .claude/scripts/issue-start.sh` (auto-détecte la 1ère TODO)
   - **OUI** → lire le fichier
     - Status IN_PROGRESS → reprendre
     - Status CHANGES_REQUESTED → appliquer les feedbacks de la section "Reviewer Feedback"
     - Status APPROVED/DONE → `bash .claude/scripts/issue-start.sh` (passe à la suivante)

### Implémentation
2. Lire `.claude/workspace/current-issue.md` — TOUT est dedans (specs, signatures, fichiers)
3. Implémenter les fichiers listés
4. `mvn test -pl <module> -q` — DOIT passer

### Fin
5. `bash .claude/scripts/issue-finish.sh`
6. NE PAS committer — le Reviewer le fera

**C'EST TOUT.** 0 autre fichier à lire. 0 tracking manuel.
```

### Reviewer Agent (`agents/reviewer.md`)

```markdown
## Protocole (1 fichier + 2 scripts)

1. Lire `.claude/workspace/current-issue.md`
2. `git diff HEAD` pour voir les changements
3. Produire verdict :
   - **APPROVED** → `bash .claude/scripts/issue-review.sh APPROVED`
   - **CHANGES_REQUESTED** → `bash .claude/scripts/issue-review.sh CHANGES_REQUESTED "raison détaillée"`
4. Si APPROVED :
   a. `git add -A && git commit -m "feat: ${ISSUE_ID} — ${TITLE}" -m "Co-Authored-By: Claude <noreply@anthropic.com>"`
   b. `bash .claude/scripts/issue-next.sh`

**C'EST TOUT.**
```

---

## 5. Nouveau `progress.md` (format sed-friendly)

```markdown
# Progress

> NE PAS CHARGER EN CONTEXTE IA. Modifié uniquement par les scripts dans `.claude/scripts/issue-*.sh`.
> Pour voir l'état : `bash .claude/scripts/progress-status.sh`

## Issues
| ID | Title | Status | PDR | Dependencies |
|----|-------|--------|-----|--------------|
| ISSUE-001 | Domain Identifiers | DONE | PDR-001 | - |
| ISSUE-002 | Domain Enums | DONE | PDR-001 | ISSUE-001 |
...

## PDRs
| ID | Name | Module | Status | Issues | Deps |
|----|------|--------|--------|--------|------|
| PDR-001 | Domain Core | platform-domain | DONE | ISSUE-001..007 | - |
...

## History
| Date | Issue | Transition | Note |
|------|-------|------------|------|
| 2026-06-22 | ISSUE-042 | TODO → IN_PROGRESS | issue-start.sh |
| 2026-06-22 | ISSUE-042 | IN_PROGRESS → IN_REVIEW | issue-finish.sh |
| 2026-06-22 | ISSUE-042 | IN_REVIEW → APPROVED | issue-review.sh |
| 2026-06-22 | ISSUE-042 | APPROVED → DONE | issue-next.sh |
```

**Règles pour les scripts :**
- `sed` opère UNIQUEMENT entre `## Issues` et `## PDRs`
- La section `## History` est append-only (`>>`)
- Aucun script ne lit jamais tout le fichier

---

## 6. `settings.json` — Permissions Portables (commit)

### Pourquoi `settings.json` et pas `settings.local.json` ?
- `settings.json` est commité → voyage avec le repo git
- `settings.local.json` est local → perdu quand on change de VM/session
- Les permissions doivent survivre : nouveau clone = tout fonctionne

### Nouveau `settings.json`

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "bash .claude/scripts/auto-commit-done.sh",
            "timeout": 60,
            "statusMessage": "Commit auto des Issues DONE..."
          }
        ]
      }
    ]
  },
  "permissions": {
    "allow": [
      "Bash(mvn *)",
      "Bash(find *)",
      "Bash(grep *)",
      "Bash(git *)",
      "Bash(sed *)",
      "Bash(awk *)",
      "Bash(mkdir -p *)",
      "Bash(chmod *)",
      "Bash(bash .claude/scripts/*)",
      "Bash(javap *)",
      "Bash(curl -s *)",
      "Bash(jar *)",
      "Bash(unzip *)",
      "Bash(echo *)",
      "Bash(sort *)",
      "Bash(xargs *)",
      "Bash(rm *)",
      "Bash(cp *)",
      "Bash(cat *)",
      "Bash(ls *)",
      "Bash(wc *)",
      "Bash(tee *)",
      "Bash(head *)",
      "Bash(tail *)",
      "Bash(cut *)",
      "Bash(tr *)",
      "Bash(python3 *)",
      "Bash(java *)",
      "Bash(docker *)",
      "Read(*)",
      "Write(.claude/**)",
      "Edit(.claude/**)",
      "Skill(*)"
    ]
  }
}
```

### `settings.local.json` — uniquement les surcharges locales

```json
{
  "permissions": {
    "allow": []
  }
}
```

→ Vidé (ou supprimé). Tout est dans `settings.json`.

### Pourquoi ces patterns sont safe :
- `Bash(mvn *)` — builds et tests, jamais destructeur hors target/
- `Bash(git *)` — uniquement sur le repo local
- `Bash(bash .claude/scripts/*)` — exécute UNIQUEMENT nos scripts (pas de bash arbitraire)
- `Write(.claude/**)` + `Edit(.claude/**)` — modifications limitées à `.claude/`
- Pas de `Bash(rm -rf /)` ou autre pattern dangereux
- Les chemins sont relatifs → portable Windows/Linux/Mac

---

## 7. `dev-loop.js` Simplifié

```javascript
// =============================================================================
// dev-loop.js — Single-Issue Develop→Review→Fix (Workflow tool)
//
// Lit current-issue.md UNE fois, passe l'info aux subagents.
// Les subagents NE lisent QUE current-issue.md.
// Toutes les transitions passent par les scripts shell.
// =============================================================================

export const meta = {
  name: 'dev-loop',
  description: 'Single-Issue Develop→Review→Fix',
  phases: [
    { title: 'Develop', detail: 'Implement the Issue' },
    { title: 'Review', detail: 'Craft + architecture review' },
    { title: 'Fix', detail: 'Apply review fixes (max 2 cycles)' },
  ],
}

const MAX_REWORK = 2

const DEV_RESULT = {
  type: 'object',
  properties: {
    issueId: { type: 'string' },
    status: { type: 'string', enum: ['IN_REVIEW', 'BLOCKED', 'ERROR'] },
    summary: { type: 'string' }
  },
  required: ['issueId', 'status']
}

const REVIEW_RESULT = {
  type: 'object',
  properties: {
    issueId: { type: 'string' },
    verdict: { type: 'string', enum: ['APPROVED', 'CHANGES_REQUESTED', 'REJECTED'] },
    summary: { type: 'string' }
  },
  required: ['issueId', 'verdict']
}

const FIX_RESULT = {
  type: 'object',
  properties: {
    issueId: { type: 'string' },
    status: { type: 'string', enum: ['IN_REVIEW', 'ERROR'] },
    appliedCount: { type: 'number' },
    summary: { type: 'string' }
  },
  required: ['issueId', 'status']
}

// ── Phase: Develop ──────────────────────────────────────────────────────────

async function developIssue() {
  const prompt = `Read .claude/workspace/current-issue.md.
If it doesn't exist or status is APPROVED/DONE, run: bash .claude/scripts/issue-start.sh
If status is CHANGES_REQUESTED, apply the Reviewer Feedback section first.

Then implement all files listed. Run: mvn test -pl <module> -q (MUST pass).
When done: bash .claude/scripts/issue-finish.sh
DO NOT commit. DO NOT read progress.md.`

  const result = await agent(prompt, {
    label: 'developer',
    phase: 'Develop',
    agentType: 'developer',
    schema: DEV_RESULT
  })
  return result || { issueId: 'UNKNOWN', status: 'ERROR', summary: 'Agent returned null' }
}

// ── Phase: Review ───────────────────────────────────────────────────────────

async function reviewIssue() {
  const prompt = `Read .claude/workspace/current-issue.md.
Run: git diff HEAD
If OK: bash .claude/scripts/issue-review.sh APPROVED
If issues: bash .claude/scripts/issue-review.sh CHANGES_REQUESTED "detailed reason"
If APPROVED: git add -A && git commit -m "feat: <ISSUE_ID> — <title>" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
Then: bash .claude/scripts/issue-next.sh`

  const result = await agent(prompt, {
    label: 'reviewer',
    phase: 'Review',
    agentType: 'reviewer',
    schema: REVIEW_RESULT
  })
  return result || { issueId: 'UNKNOWN', verdict: 'REJECTED', summary: 'Agent returned null' }
}

// ── Phase: Fix ──────────────────────────────────────────────────────────────

async function fixIssue() {
  const prompt = `Read .claude/workspace/current-issue.md.
Apply all fixes from the "Reviewer Feedback" section.
Run: mvn test -pl <module> -q (MUST pass).
When all fixes applied: bash .claude/scripts/issue-finish.sh
DO NOT commit.`

  const result = await agent(prompt, {
    label: 'fixer',
    phase: 'Fix',
    agentType: 'developer',
    schema: FIX_RESULT
  })
  return result || { issueId: 'UNKNOWN', status: 'ERROR', appliedCount: 0, summary: 'Agent returned null' }
}

// ── Main ────────────────────────────────────────────────────────────────────

async function main() {
  log('🎯 Dev-Loop: Develop → Review → Fix')
  log('')

  // 1. DEVELOP
  log('🔨 Phase: Develop...')
  const devResult = await developIssue()

  if (devResult.status !== 'IN_REVIEW') {
    log(`❌ Develop: ${devResult.status} — ${devResult.summary || ''}`)
    return { status: devResult.status }
  }
  log(`✅ ${devResult.issueId} → IN_REVIEW`)

  // 2. REVIEW + FIX loop
  let reworkCount = 0

  while (reworkCount <= MAX_REWORK) {
    log(`📝 Phase: Review${reworkCount > 0 ? ' (re-review)' : ''}...`)
    const reviewResult = await reviewIssue()

    if (reviewResult.verdict === 'APPROVED') {
      log(`✅ ${reviewResult.issueId} APPROVED → DONE`)
      return { verdict: 'APPROVED', reworkCycles: reworkCount }
    }

    if (reviewResult.verdict === 'REJECTED') {
      log(`❌ REJECTED: ${reviewResult.summary || ''}`)
      return { verdict: 'REJECTED' }
    }

    // CHANGES_REQUESTED
    if (reworkCount >= MAX_REWORK) {
      log(`⚠️  Max rework (${MAX_REWORK}) reached`)
      return { verdict: 'MAX_REWORK', reworkCycles: reworkCount }
    }

    reworkCount++
    log(`🔧 Phase: Fix (${reworkCount}/${MAX_REWORK})...`)
    const fixResult = await fixIssue()
    log(`   ${fixResult.appliedCount || '?'} fixes applied, status=${fixResult.status}`)
  }

  return { verdict: 'COMPLETED', reworkCycles: reworkCount }
}

await main()
```

---

## 8. Mise à jour des Fichiers Existants

### 8.1 `CLAUDE.md` — Nouvelle table de routing

Les chemins changent, la table de routing doit être mise à jour :

| Avant | Après |
|-------|-------|
| `.claude/specifications/` | `.claude/knowledge/specs/` |
| `.claude/adr/` | `.claude/knowledge/adr/` |
| `.claude/skills/` | `.claude/knowledge/skills/` |
| `.claude/architecture.md` | `.claude/knowledge/architecture.md` |
| `.claude/glossary.md` | `.claude/knowledge/glossary.md` |
| `.claude/roadmap.md` | `.claude/knowledge/roadmap.md` |
| `.claude/constraints.md` | `.claude/knowledge/constraints.md` |
| `.claude/progress.md` | `.claude/workspace/progress.md` |
| `.claude/session-state.md` | `.claude/workspace/session-state.md` |
| `.claude/context/interfaces-registry.md` | `.claude/workspace/interfaces-registry.md` |
| `.claude/context/recommendations-tracking.md` | `.claude/workspace/recommendations-tracking.md` |
| `.claude/context/decisions-log.md` | `.claude/workspace/decisions-log.md` |
| `.claude/context/dependency-map.md` | `.claude/workspace/dependency-map.md` |
| `.claude/context/known-issues.md` | `.claude/workspace/known-issues.md` |
| `.claude/issues/` | `.claude/workspace/issues/` |
| `.claude/pdr/` | `.claude/workspace/issues/` (fusionné) |

### 8.2 `dev-loop.sh` — Adaptation

```bash
# Le script ne change pas de logique, juste :
PROGRESS_FILE="$PROJECT_ROOT/.claude/workspace/progress.md"
# Au lieu de :
PROGRESS_FILE="$PROJECT_ROOT/.claude/progress.md"
```

### 8.3 `auto-commit-done.sh` — Adaptation

```bash
# Changement du path de progress.md :
# De : .claude/progress.md
# À  : .claude/workspace/progress.md
```

---

## 9. Impact

| Métrique | Avant | Après |
|----------|-------|-------|
| **Fichiers lus par Developer** | 4-5 (~4500 tokens) | **1** (~800 tokens) |
| **Fichiers lus par Reviewer** | 5+ (~3000 tokens) | **1** (~800 tokens) |
| **Écritures tracking manuelles** | 3 par Issue | **0** (scripts) |
| **Permissions** | 144 one-shot (`.local.json`) | **~28 patterns** (`.settings.json`) |
| **Portabilité permissions** | ❌ local non-commit | ✅ commit dans `settings.json` |
| **Subagent autonome** | ❌ dépend de fichiers hors portée | ✅ tout dans `.claude/` |
| **1ère itération** | Manuel (humain doit lancer) | Auto (`issue-start.sh` sans args) |
| **Changement VM** | Reconfig permissions | Git clone → tout marche |
| **Risque désynchro** | Élevé (5 fichiers) | **Faible** (1 fichier lu, scripts écrivent) |
| **Dossiers racine** | `tracking/`, `src/`, `.claude/context/` | **Un seul : `.claude/`** |

---

## 10. Plan de Migration

### Phase A — Setup (safe, pas de code changé)
1. Créer `.claude/workspace/`
2. Fusionner `tracking/*` + `.claude/context/*` → `.claude/workspace/`
3. Déplacer `.claude/session-state.md` → `.claude/workspace/`
4. Déplacer `.claude/progress.md` → `.claude/workspace/`
5. Déplacer `.claude/issues/` → `.claude/workspace/issues/`
6. Déplacer `.claude/pdr/` → `.claude/workspace/issues/`
7. Supprimer `tracking/`, `src/`, `.claude/context/`, `.claude/feature-summaries/`
8. Ajouter `platform-examples` au `pom.xml`
9. `mvn compile` pour vérifier

### Phase B — Scripts
1. Créer les 5 scripts dans `.claude/scripts/`
2. `chmod +x` chaque script
3. Tester `progress-status.sh` (lecture seule, safe)
4. Tester `issue-start.sh ISSUE-XXX` sur une Issue TODO
5. Tester `issue-finish.sh` → `issue-review.sh APPROVED` → `issue-next.sh` en séquence
6. Reformater `progress.md` au nouveau format sed-friendly

### Phase C — Configuration
1. Remplacer `settings.json` par la nouvelle version (permissions + hooks)
2. Vider `settings.local.json`
3. Mettre à jour `CLAUDE.md` (table de routing)
4. Mettre à jour `agents/developer.md` et `agents/reviewer.md`
5. Mettre à jour `dev-loop.js` et `dev-loop.sh`
6. Mettre à jour `auto-commit-done.sh` + `commit-done-issues.sh`
7. Mettre à jour `prompts/session-bootstrap.md`

### Phase D — Knowledge
1. Créer `.claude/knowledge/`
2. Déplacer `specifications/` → `knowledge/specs/`
3. Déplacer `adr/` → `knowledge/adr/`
4. Déplacer `skills/` → `knowledge/skills/`
5. Déplacer `architecture.md`, `glossary.md`, `roadmap.md`, `constraints.md` → `knowledge/`
6. Mettre à jour TOUS les liens internes dans les fichiers `.md`

### Phase E — Premier run
1. `rm .claude/workspace/current-issue.md` (si existe)
2. `bash .claude/scripts/progress-status.sh` → vérifier l'état
3. Lancer un `dev-loop.js` avec le nouveau flow
4. Vérifier que tout le cycle Develop→Review→Fix→Next fonctionne

---

## 11. Fichiers Impactés — Liste Exhaustive

### Fichiers à créer
- `.claude/scripts/issue-start.sh`
- `.claude/scripts/issue-finish.sh`
- `.claude/scripts/issue-review.sh`
- `.claude/scripts/issue-next.sh`
- `.claude/scripts/issue-block.sh`
- `.claude/scripts/progress-status.sh`
- `.claude/workspace/current-issue.md` (sera créé par issue-start.sh au 1er run)

### Fichiers à modifier (chemins)
- `.claude/settings.json` — ajout permissions + mise à jour hook path
- `.claude/settings.local.json` — vider
- `CLAUDE.md` — section 8 (table de routing)
- `.claude/agents/developer.md` — protocole simplifié
- `.claude/agents/reviewer.md` — protocole simplifié
- `.claude/agents/system-designer.md` — paths vers workspace/issues/
- `.claude/workflows/dev-loop.js` — réécrit
- `.claude/scripts/dev-loop.sh` — path progress.md
- `.claude/scripts/auto-commit-done.sh` — path progress.md
- `.claude/scripts/commit-done-issues.sh` — path progress.md
- `.claude/prompts/session-bootstrap.md` — nouveaux paths
- `.claude/guides/agent-orchestration.md` — nouveaux paths
- `.claude/guides/how-to-start.md` — nouveaux paths
- `.claude/commands/done.md` — script references
- `.claude/commands/next.md` — script references
- `.claude/commands/review.md` — script references

### Fichiers à déplacer (mv)
- `.claude/specifications/` → `.claude/knowledge/specs/`
- `.claude/adr/` → `.claude/knowledge/adr/`
- `.claude/skills/` → `.claude/knowledge/skills/`
- `.claude/architecture.md` → `.claude/knowledge/architecture.md`
- `.claude/glossary.md` → `.claude/knowledge/glossary.md`
- `.claude/roadmap.md` → `.claude/knowledge/roadmap.md`
- `.claude/constraints.md` → `.claude/knowledge/constraints.md`
- `.claude/progress.md` → `.claude/workspace/progress.md`
- `.claude/session-state.md` → `.claude/workspace/session-state.md`
- `.claude/context/*` → `.claude/workspace/`
- `.claude/issues/*` → `.claude/workspace/issues/`
- `.claude/pdr/*` → `.claude/workspace/issues/`
- `tracking/*` → `.claude/workspace/` (doublons, seront écrasés)

### Fichiers à supprimer
- `tracking/` (dossier entier)
- `src/` (dossier vide à la racine)
- `.claude/context/` (dossier après move)
- `.claude/specifications/` (dossier après move)
- `.claude/feature-summaries/` (dossier)
- `.claude/issues/` (dossier après move)
- `.claude/pdr/` (dossier après move)

### Fichiers non-impactés
- Tous les modules Maven (`platform-*/`) — aucun changement
- `.claude/commands/` (sauf 3 fichiers à adapter)
- `.claude/guides/` (chemins à mettre à jour)
- `pom.xml` (juste ajout `platform-examples`)

---

## Annexe : Flux Complet Developer

```
┌─────────────────────────────────────────────────────────────┐
│ Developer Agent démarre                                      │
│                                                              │
│ 1. current-issue.md existe ?                                 │
│    ├─ NON → bash .claude/scripts/issue-start.sh             │
│    │        → grep TODO dans progress.md                     │
│    │        → sed IN_PROGRESS                                │
│    │        → crée current-issue.md (inline)                 │
│    │        → lit current-issue.md                           │
│    │                                                         │
│    └─ OUI → lire current-issue.md                           │
│       ├─ Status IN_PROGRESS → reprendre                     │
│       ├─ Status CHANGES_REQUESTED → appliquer feedbacks     │
│       └─ Status APPROVED/DONE → issue-start.sh              │
│                                                                  │
│ 2. Implémenter (tout est dans current-issue.md)                 │
│                                                                  │
│ 3. mvn test -pl <module> -q                                     │
│                                                                  │
│ 4. bash .claude/scripts/issue-finish.sh                         │
│    → sed IN_REVIEW dans progress.md                              │
│    → met à jour current-issue.md status                          │
│                                                                  │
│ FIN — 0 commit, 0 autre fichier lu/écrit                        │
└─────────────────────────────────────────────────────────────┘
```

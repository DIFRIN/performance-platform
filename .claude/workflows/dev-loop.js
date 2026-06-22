// =============================================================================
// dev-loop.js — Single-Issue Develop→Review→Fix workflow (Workflow tool)
//
// Handles ONE Issue end-to-end within a single Workflow tool invocation.
// For multi-Issue looping with fresh context each time, use dev-loop.sh.
//
// Usage (from Workflow tool):
//   Workflow({scriptPath: ".claude/workflows/dev-loop.js", args: {issueId: "ISSUE-090"}})
// =============================================================================

export const meta = {
  name: 'dev-loop',
  description: 'Single-Issue Develop→Review→Fix. For multi-Issue loop use dev-loop.sh.',
  phases: [
    { title: 'Develop', detail: 'Implement the Issue' },
    { title: 'Review', detail: 'Craft + architecture review' },
    { title: 'Fix', detail: 'Apply review fixes (max 2 cycles)' },
  ],
}

const MAX_REWORK = 2

// ─── Schemas ──────────────────────────────────────────────────────────────────

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
    blockerCount: { type: 'number' },
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

// ─── Phase: Develop ───────────────────────────────────────────────────────────

async function developIssue(issueId, pdrId) {
  const prompt = `ISSUE ${issueId} (PDR: ${pdrId || 'unknown'}) — implement it.

Follow your Developer protocol (.claude/agents/developer.md):
1. Read .claude/session-state.md, .claude/progress.md
2. If WAITING → Edit progress.md: WAITING→IN PROGRESS + history entry
3. Read .claude/issues/${issueId}-*.md
4. Implement ALL files listed in the Issue
5. Run "mvn test -pl <module> -q" — MUST pass
6. Edit .claude/progress.md: IN PROGRESS→IN REVIEW + history entry
7. Update .claude/session-state.md + .claude/context/interfaces-registry.md
8. DO NOT commit.

CRITICAL: Use the Edit tool to actually change progress.md. Do not just describe.`

  const result = await agent(prompt, {
    label: `dev-${issueId}`,
    phase: 'Develop',
    agentType: 'developer',
    schema: DEV_RESULT
  })
  return result || { issueId, status: 'ERROR', summary: 'Agent returned null' }
}

// ─── Phase: Review ────────────────────────────────────────────────────────────

async function reviewIssue(issueId, pdrId) {
  const prompt = `Review ${issueId} (IN REVIEW in .claude/progress.md).

Follow your Reviewer protocol (.claude/agents/reviewer.md):
1. Read .claude/progress.md — confirm IN REVIEW
2. Read .claude/issues/${issueId}-*.md
3. Read .claude/pdr/${pdrId || 'PDR-XXX'}-*.md for signatures
4. Review all changed files (git diff + git diff --cached)
5. Produce verdict: APPROVED or CHANGES_REQUESTED
6. If APPROVED:
   a. Edit progress.md: IN REVIEW→DONE + history entry
   b. Update interfaces-registry.md (→STABLE)
   c. Update session-state.md
   d. git add -A && git commit -m "feat: ${issueId} — ..." -m "Co-Authored-By: Claude <noreply@anthropic.com>"
7. If CHANGES_REQUESTED:
   a. Edit progress.md: IN REVIEW→CHANGES_REQUESTED + history entry
   b. Write PENDING recommendations to .claude/context/recommendations-tracking.md
   c. Update session-state.md
   d. DO NOT commit

CRITICAL: Use the Edit tool to actually change progress.md.`

  const result = await agent(prompt, {
    label: `review-${issueId}`,
    phase: 'Review',
    agentType: 'reviewer',
    schema: REVIEW_RESULT
  })
  return result || { issueId, verdict: 'REJECTED', blockerCount: 1, summary: 'Agent returned null' }
}

// ─── Phase: Fix ───────────────────────────────────────────────────────────────

async function fixIssue(issueId) {
  const prompt = `Apply PENDING recommendations for ${issueId} from .claude/context/recommendations-tracking.md.

1. Read .claude/context/recommendations-tracking.md
2. For each [PENDING] recommendation: apply fix, run tests, mark [APPLIED]
3. When all APPLIED:
   a. Edit .claude/progress.md: CHANGES_REQUESTED→IN REVIEW + history entry
   b. Update .claude/session-state.md
4. DO NOT commit.

CRITICAL: Use the Edit tool to actually change the files.`

  const result = await agent(prompt, {
    label: `fix-${issueId}`,
    phase: 'Fix',
    agentType: 'developer',
    schema: FIX_RESULT
  })
  return result || { issueId, status: 'ERROR', appliedCount: 0, summary: 'Agent returned null' }
}

// ─── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  const issueId = (args && args.issueId) ? args.issueId : null
  const pdrId = (args && args.pdrId) ? args.pdrId : 'UNKNOWN'

  if (!issueId) {
    log('⚠️  No issueId provided. Use: Workflow({args: {issueId: "ISSUE-090"}})')
    return { status: 'NO_ISSUE' }
  }

  log(`🎯 ${issueId} (PDR: ${pdrId})`)
  log('')

  // 1. DEVELOP
  log(`🔨 Developing ${issueId}...`)
  const devResult = await developIssue(issueId, pdrId)

  if (devResult.status !== 'IN_REVIEW') {
    log(`❌ Develop: ${devResult.status} — ${devResult.summary || ''}`)
    return { issueId, status: devResult.status }
  }
  log(`✅ ${issueId} → IN REVIEW`)

  // 2. REVIEW + FIX loop
  let reworkCount = 0

  while (reworkCount <= MAX_REWORK) {
    const reviewResult = await reviewIssue(issueId, pdrId)
    log(`📝 Review: ${reviewResult.verdict} (${reviewResult.blockerCount || 0}B)`)

    if (reviewResult.verdict === 'APPROVED') {
      log(`✅ ${issueId} APPROVED → DONE`)
      return { issueId, verdict: 'APPROVED', reworkCycles: reworkCount }
    }

    if (reviewResult.verdict === 'REJECTED') {
      log(`❌ ${issueId} REJECTED: ${reviewResult.summary || ''}`)
      return { issueId, verdict: 'REJECTED' }
    }

    // CHANGES_REQUESTED
    if (reworkCount >= MAX_REWORK) {
      log(`⚠️  Max rework (${MAX_REWORK}) for ${issueId}`)
      return { issueId, verdict: 'MAX_REWORK', reworkCycles: reworkCount }
    }

    reworkCount++
    log(`🔧 Fix ${reworkCount}/${MAX_REWORK}...`)
    const fixResult = await fixIssue(issueId)
    log(`   ${fixResult.appliedCount || 0} applied`)
  }

  return { issueId, verdict: 'DONE', reworkCycles: reworkCount }
}

await main()

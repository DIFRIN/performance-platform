// =============================================================================
// dev-loop.js — Single-Issue Develop→Review→Fix (Workflow tool)
//
// Les subagents ne lisent QUE .claude/workspace/current-issue.md.
// Toutes les transitions passent par les scripts shell.
//
// Usage: Workflow({scriptPath: ".claude/workflows/dev-loop.js"})
// =============================================================================

export const meta = {
  name: 'dev-loop',
  description: 'Single-Issue Develop→Review→Fix. Agents read only current-issue.md.',
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
If status is CHANGES_REQUESTED, apply fixes from the Reviewer Feedback section first.
Implement all files listed. Run: mvn test -pl <module> -q (MUST pass).
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
Then: bash .claude/scripts/issue-next.sh
DO NOT read progress.md.`

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
Apply all fixes from the Reviewer Feedback section.
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

    if (reworkCount >= MAX_REWORK) {
      log(`⚠️  Max rework (${MAX_REWORK}) reached`)
      return { verdict: 'MAX_REWORK', reworkCycles: reworkCount }
    }

    reworkCount++
    log(`🔧 Phase: Fix (${reworkCount}/${MAX_REWORK})...`)
    const fixResult = await fixIssue()
    log(`   ${fixResult.appliedCount || '?'} fixes applied, status=${fixResult.status}`)
  }
}

await main()

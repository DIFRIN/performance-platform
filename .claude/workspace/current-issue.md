# ISSUE-127: Vue liste des executions (polling 3s, filtre statut, cancel/delete)
**Status**: APPROVED
**PDR**: PDR-029
**Module**: platform-app
**Started**: 2026-06-23T21:08+02:00
**IssueFile**: issues/ISSUE-127-view-executions-list.md

> 📄 Full specification: `.claude/workspace/issues/ISSUE-127-view-executions-list.md`

## Reviewer Feedback
(None yet)

---
## Reviewer Feedback — 2026-06-23T21:42+02:00
BUG: api.js request() function cannot handle 202 Accepted with empty body. The POST /api/v1/executions/{id}/cancel endpoint returns ResponseEntity.accepted().build() (202 with no body). The request() function only special-cases status 204 for null return; for 202 it calls res.json() on an empty body, which throws SyntaxError. This causes the cancel action to appear to fail (alert) even though the server accepted it successfully. Fix: extend the empty-body check to cover 202 (or use res.text() and check for empty string before JSON.parse).

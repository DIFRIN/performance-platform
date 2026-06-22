# Commande /review — Lancer une review sur l'Issue IN REVIEW

Déléguer immédiatement au subagent @reviewer.

Le reviewer va :
1. Identifier l'Issue IN REVIEW via `.claude/workspace/current-issue.md`
2. Passer les checklists ARCH / CRAFT / TEST / SPEC
3. Produire le verdict et exécuter le script correspondant :
   - **APPROVED** → `bash .claude/scripts/issue-review.sh APPROVED`
   - **CHANGES_REQUESTED** → `bash .claude/scripts/issue-review.sh CHANGES_REQUESTED "raison"`
4. Si APPROVED : commit + `bash .claude/scripts/issue-next.sh`
5. Mettre à jour `.claude/workspace/recommendations-tracking.md` si recommandations PENDING

# Commande /review — Lancer une review sur l'Issue IN REVIEW

Déléguer immédiatement au subagent @reviewer.

Le reviewer va :
1. Identifier l'Issue IN REVIEW dans `.claude/workspace/progress.md`
2. Passer les checklists ARCH / CRAFT / TEST / SPEC
3. Produire le rapport APPROVED | CHANGES_REQUESTED | REJECTED
4. Mettre à jour `.claude/workspace/progress.md` et `.claude/workspace/interfaces-registry.md` selon le verdict

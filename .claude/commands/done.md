# Commande /done — Marquer l'Issue courante IN REVIEW et préparer le commit

Lire `.claude/workspace/progress.md` et `.claude/workspace/session-state.md` pour identifier l'Issue IN PROGRESS.

Effectuer dans l'ordre :

1. Vérifier les critères de done de `.claude/workspace/issues/ISSUE-XXX.md` — tous cochés ?
   Si non : lister les critères manquants et STOP.

2. Mettre à jour `.claude/workspace/progress.md` :
   - ISSUE-XXX : IN PROGRESS → IN REVIEW
   - Ajouter ligne historique : `[date] ISSUE-XXX : IN PROGRESS → IN REVIEW (Developer)`

3. Mettre à jour `.claude/workspace/interfaces-registry.md` :
   - Toutes les interfaces créées dans cette Issue : ⬜/🔄 → 🔄 IN PROGRESS

4. Mettre à jour `.claude/workspace/session-state.md` :
   - Statut issue → IN REVIEW
   - Dernière action → "Issue passée IN REVIEW, en attente Reviewer"
   - Prochaine action → "Lancer @reviewer"

5. Afficher le message de commit à copier :
   ```
   feat(ISSUE-XXX): <titre de l'issue>

   Modules: platform-xxx
   Tests: X unitaires ajoutés
   Status: IN REVIEW — en attente Reviewer
   ```

Ne pas effectuer le commit — afficher uniquement le message pour validation humaine.

# Commande /done — Marquer l'Issue courante IN REVIEW et préparer le commit

Lire `.claude/workspace/current-issue.md` pour identifier l'Issue IN PROGRESS.

Effectuer dans l'ordre :

1. Vérifier les critères de done de `.claude/workspace/issues/ISSUE-XXX.md` — tous cochés ?
   Si non : lister les critères manquants et STOP.

2. Exécuter le script de transition :
   ```bash
   bash .claude/scripts/issue-finish.sh
   ```
   → IN_PROGRESS → IN_REVIEW dans progress.md + current-issue.md

3. Mettre à jour `.claude/workspace/interfaces-registry.md` :
   - Toutes les interfaces créées dans cette Issue : 🔄 IN PROGRESS

4. Afficher le résumé :
   ```
   ✅ ISSUE-XXX → IN REVIEW
   Module: platform-xxx
   En attente de review (@reviewer)
   ```

Ne pas committer — c'est le Reviewer qui commit après APPROVED.

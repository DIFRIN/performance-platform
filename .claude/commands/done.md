# Commande /done — Marquer l'Issue courante IN REVIEW et préparer le commit

Lire `progress.md` et `session-state.md` pour identifier l'Issue IN PROGRESS.

Effectuer dans l'ordre :

1. Vérifier les critères de done de `issues/ISSUE-XXX.md` — tous cochés ?
   Si non : lister les critères manquants et STOP.

2. Mettre à jour `progress.md` :
   - ISSUE-XXX : IN PROGRESS → IN REVIEW
   - Ajouter ligne historique : `[date] ISSUE-XXX : IN PROGRESS → IN REVIEW (Developer)`

3. Mettre à jour `context/interfaces-registry.md` :
   - Toutes les interfaces créées dans cette Issue : ⬜/🔄 → 🔄 IN PROGRESS

4. Mettre à jour `session-state.md` :
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

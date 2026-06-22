# Commande /next — Prochaine action à faire

Lis `.claude/workspace/session-state.md` puis exécute :

```bash
bash .claude/scripts/progress-status.sh
```

Réponds en moins de 10 lignes :

```
ISSUE ACTIVE    : [ISSUE-XXX — titre] | Aucune
STATUT          : [IN PROGRESS / IN REVIEW / APPROVED / —]
PROCHAINE ACTION: [reprendre l'implémentation | lancer @reviewer | issue-next.sh | issue-start.sh]
```
**Résumé rapide d'avancement** : [stats de progress-status.sh]

Ne lire que session-state.md et exécuter progress-status.sh. Ne pas charger progress.md en contexte.
Ne pas démarrer de travail. Attendre instruction.

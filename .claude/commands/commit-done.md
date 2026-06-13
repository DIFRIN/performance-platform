# Commande /commit-done — Créer les commits pour toutes les Issues DONE non committées

Exécute `scripts/commit-done-issues.sh` pour générer des Conventional Commits
pour chaque Issue DONE dans `progress.md` qui n'a pas encore de commit git.

---

## Processus

1. **Dry run d'abord** — afficher ce qui sera commité sans agir :
   ```bash
   bash scripts/commit-done-issues.sh --dry-run
   ```

2. **Afficher le résultat** à l'utilisateur pour validation.

3. **Si l'utilisateur confirme**, exécuter :
   ```bash
   bash scripts/commit-done-issues.sh
   ```

4. **Afficher le log git** des commits créés :
   ```bash
   git log --oneline -10
   ```

---

## Format des commits produits

```
feat(domain): implement execution context immutable record

Module: platform-domain
Size: M

Immuable record avec with() copy-on-write et defensive copy dans le constructeur compact.

Refs: ISSUE-003
```

---

## Règles

- Les Issues sont committées dans l'ordre d'apparition dans `progress.md`
- Une Issue sans fichier `issues/ISSUE-XXX-*.md` est ignorée avec un warning
- Une Issue déjà committée (détectée via `Refs: ISSUE-XXX` dans le log) est ignorée
- Si rien n'est staged, le commit est ignoré avec un message clair
- Les fichiers non trackés (`git add` non fait) ne sont pas auto-stagés

## Types Conventional Commits dérivés automatiquement

| Contexte | Type |
|---|---|
| Module contient `domain`, `dsl`, `scenario`, `execution` | `feat` |
| Module contient `test`, `testcontainer` | `test` |
| Titre/module contient `docker`, `k8s`, `deploy`, `setup` | `chore` |
| Titre contient `fix`, `bugfix` | `fix` |
| Titre contient `refactor`, `cleanup` | `refactor` |
| Titre contient `doc`, `readme` | `docs` |
| Défaut | `feat` |

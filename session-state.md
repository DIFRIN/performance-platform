# Session State

> CE FICHIER EST CRITIQUE. Mis à jour à la FIN de chaque session. Lu EN PREMIER au démarrage.
> Permet la reprise exacte sans re-lire tout le projet.
>
> Si vide ou périmé : lire `progress.md` pour retrouver l'Issue IN PROGRESS ou la prochaine WAITING.

---

## État Courant

**Date dernière session** : [YYYY-MM-DD HH:MM]
**Agent actif** : [ ] System Designer | [ ] Architect | [ ] Developer | [ ] Reviewer | [ ] Tester
**Issue active** : [ISSUE-XXX — titre] | _Aucune_
**Statut issue** : [ ] WAITING | [ ] IN PROGRESS | [ ] IN REVIEW | [ ] DONE
**PDR parent** : [PDR-XXX — titre]

---

## Reprise Exacte

> Section la plus importante. Remplie par l'agent en fin de session.

**Dernière action** :
[Ex: "Créé ExecutionContext.java avec constructeur compact + test immutabilité. mvn test OK."]

**Prochaine action** :
[Ex: "Créer ExecutionPlan.java — record avec List<ExecutionStep> trié par dagLevel"]

**Fichiers en cours** :
```
platform-domain/src/main/java/com/performance/platform/domain/campaign/
  ├── ExecutionContext.java     ✅ terminé + testé
  ├── ExecutionPlan.java        🔄 à créer
  └── ExecutionStep.java        ⬜ à créer après ExecutionPlan
```

**Blocages** :
[Ex: "Ambiguïté sur le type de dagLevel dans ExecutionStep — entier ou calculé dynamiquement ?"]
[_Aucun_ si pas de blocage]

---

## Fichiers à Charger à la Prochaine Session

> Calculés par l'agent — minimum strict.

```
TOUJOURS :
  session-state.md                (ce fichier)
  progress.md                     (Issue à prendre)
  issues/ISSUE-XXX.md             (l'issue active — ID ci-dessus)

SI REPRISE (issue IN PROGRESS) :
  ← c'est tout, les infos sont dans ce fichier

SI NOUVELLE ISSUE :
  agents/developer.md             (section "Protocole de Démarrage" uniquement)
  context/interfaces-registry.md  (module concerné par la nouvelle issue)
  pdr/PDR-XXX.md                  (si l'issue référence un PDR non encore lu)
```

---

## Historique Sessions (1 ligne par session)

| Date | Agent | Issue | Action | Résultat |
|---|---|---|---|---|
| [date] | System Designer | — | Création PDRs + Issues | ✅ progress.md initialisé |
| [date] | Developer | ISSUE-001 | Impl ExecutionContext | 🔄 en cours |

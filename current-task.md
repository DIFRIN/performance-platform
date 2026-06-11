# Current Task

> ⚠️ RÔLE SECONDAIRE — Ce fichier sert de scratchpad pour la session en cours.
> La SOURCE DE VÉRITÉ du plan est `progress.md` + `issues/ISSUE-XXX.md`.
>
> Le Developer lit `progress.md` pour choisir son Issue, puis `issues/ISSUE-XXX.md`
> pour le détail. Ce fichier peut être utilisé pour des notes de session
> ou pour les sessions Architect/Reviewer qui ne travaillent pas sur une Issue.

---

## Tâche Courante

**ID** : [Ex: 1.1]
**Nom** : [Nom de la tâche dans task-breakdown.md]
**Phase Roadmap** : Phase X — [Nom]
**Statut** : [ ] Non démarré | [ ] En cours | [ ] En review | [ ] En test | [ ] ✅ Terminé
**Agent actif** : [ ] Developer | [ ] Reviewer | [ ] Tester | [ ] Architect

---

## Reprise de Session

> Rempli par l'agent en fin de session. Lu en premier à la reprise.

**Dernière action** : [Ex: "Créé ExecutionContext + test. Build OK."]
**Prochaine action** : [Ex: "Créer ExecutionPlan record dans platform-domain/domain/campaign/"]
**Fichiers en cours** :
```
platform-xxx/src/.../
  ├── FichierA.java    ✅
  ├── FichierB.java    🔄 (méthode buildXxx() à finir)
  └── FichierC.java    ⬜
```

---

## Contexte

[Description en 3-5 lignes du QUOI et du POURQUOI dans le contexte de la phase.]

---

## Specs de Référence

> Lire UNIQUEMENT ces fichiers — pas les autres.

| Fichier | Sections à lire |
|---|---|
| `specifications/XX-nom.md` | sections X, Y uniquement |
| `skills/nom.md` | complet |

---

## Périmètre Exact

**Classes à créer** :
```
platform-xxx/src/main/java/com/performance/platform/xxx/
  ├── ClasseA.java      — [rôle en 1 ligne]
  └── ClasseB.java      — [rôle en 1 ligne]
```

**Classes à modifier** :
```
platform-xxx/.../ClasseExistante.java — [quelle méthode, pourquoi]
```

**Hors scope (ne pas toucher)** :
- [Ce qui ne doit PAS être fait dans cette tâche]

---

## Interfaces à Implémenter

> Copier ici les signatures exactes. L'agent ne doit pas aller les chercher dans les specs.

```java
public interface NomInterface {
    ReturnType method(ParamType param);
}

public record NomRecord(
    Type field1,
    Type field2
) {}
```

---

## Critères de Done

- [ ] [Critère 1 — vérifiable objectivement]
- [ ] [Critère 2]
- [ ] `mvn test -pl platform-xxx -q` passe sans erreur
- [ ] `mvn test -pl platform-xxx -P integration-tests` si tests d'intégration
- [ ] Aucun warning de compilation (`mvn compile -pl platform-xxx 2>&1 | grep WARN`)
- [ ] Coverage domaine > 90% si `platform-domain` modifié
- [ ] `context/interfaces-registry.md` mis à jour (PLANNED → IN PROGRESS)
- [ ] `session-state.md` mis à jour en fin de session

---

## Dépendances

**Prérequis STABLE** : [Voir `context/dependency-map.md` — tâche X.X]
**Bloqué par** : [Si bloqué, description précise + escalade Architect si architectural]

---

## Décisions Locales

> Micro-décisions prises pendant cette tâche. À copier dans `context/decisions-log.md`.

| Date | Décision | Raison |
|---|---|---|
| — | — | — |

---

## Fin de Tâche — Checklist

Avant de marquer ✅ Terminé :

- [ ] Toutes les classes du périmètre créées
- [ ] Tests unitaires écrits et passants
- [ ] `context/interfaces-registry.md` mis à jour (IN PROGRESS → STABLE après review)
- [ ] `context/decisions-log.md` mis à jour
- [ ] `session-state.md` mis à jour (section "Historique des Sessions")
- [ ] `feature-summaries/README.md` mis à jour (après APPROVED)
- [ ] Statut de cette tâche → "En review" (pas "Terminé" avant APPROVED)

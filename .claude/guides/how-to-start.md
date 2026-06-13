# Guide — Démarrage du Projet

> Ce guide répond à : "J'ai ce repo, par où je commence ?"
> À lire UNE SEULE FOIS au démarrage. Ensuite, suivre `.claude/roadmap.md` et `.claude/current-task.md`.

---

## Pré-requis Humain

Avant de lancer le premier agent, vérifier :
- [ ] Java 25 installé (`java -version`)
- [ ] Maven 3.9+ installé (`mvn -version`)
- [ ] Docker installé (pour Testcontainers)
- [ ] Accès à un registre Docker si déploiement K8s prévu

---

## Étape 1 — Comprendre la Structure

```
Lire dans cet ordre (20 minutes) :
1. .claude/glossary.md          ← Le vocabulaire. Tout le reste en dépend.
2. .claude/architecture.md      ← La structure Maven + règles de dépendances
3. .claude/constraints.md       ← Ce qui ne change jamais
4. .claude/roadmap.md           ← Les 10 phases dans l'ordre
5. CLAUDE.md            ← Comment les agents travaillent
```

Ne pas lire les specs en détail maintenant — elles seront lues au moment des tâches.

---

## Étape 2 — Activer le System Designer (PREMIÈRE CHOSE À FAIRE)

Avant tout développement, le System Designer lit les specs et crée le plan complet.

```
Utiliser le prompt dans : .claude/agents/system-designer.md
```

Le System Designer va :
1. Lire toutes les specs (15-20 min)
2. Proposer la liste des PDRs (validation humaine requise)
3. Créer les fichiers `.claude/pdr/PDR-XXX.md` et `.claude/issues/ISSUE-XXX.md`
4. Initialiser `.claude/progress.md` avec tous les PDRs et Issues

**Ne pas activer le Developer avant que `.claude/progress.md` soit initialisé.**

---

## Étape 3 — Préparer la Première Session Developer

Après que `.claude/progress.md` est initialisé :

```markdown
## Tâche Courante

**Nom** : Structure Maven + platform-domain
**Phase Roadmap** : Phase 1 — Foundation (Domaine + DSL)
**Statut** : [ ] Non démarré

## Contexte

Créer la structure Maven multi-module complète et implémenter
toutes les classes du domaine (entités, value objects, events)
telles que définies dans le glossaire et les interfaces de spec-01/02.

## Specs de Référence
- `.claude/specifications/01-scenario-dsl.md` section 6 (modèle de données)
- `.claude/specifications/02-execution-engine.md` sections 2, 3, 4
- `.claude/architecture.md` section 2 (structure Maven)
- `.claude/skills/hexagonal-architecture.md`

## Périmètre de la Tâche

**Inclus** :
- [ ] pom.xml parent avec tous les modules déclarés
- [ ] pom.xml de chaque module (dépendances correctes)
- [ ] Toutes les classes de platform-domain (records, enums, events)
- [ ] Tests unitaires pour chaque record/value object

**Exclus** :
- Aucun code Spring dans cette tâche
- Pas de parseur YAML encore
- Pas de JPA encore

## Interfaces à Respecter

Voir `.claude/specifications/01-scenario-dsl.md` section 6 et
`.claude/specifications/02-execution-engine.md` sections 3 et 4.

## Critères de Done
- [ ] `mvn compile` passe sur tous les modules
- [ ] 0 dépendance Spring dans platform-domain
- [ ] Tous les termes du glossaire ont leur classe/record correspondant
- [ ] Tests unitaires pour l'immutabilité de ExecutionContext
```

---

## Étape 4 — Lancer le Developer

### Session Architect (optionnel mais recommandé)
```
Instruction d'activation :
"Tu es l'agent Architect de ce projet.
Lis agents/architect.md.
Lis architecture.md, constraints.md, .claude/adr/*.md.
Valide que .claude/current-task.md pour la Phase 1 est cohérent
avec les specs et l'architecture. Identifie tout manque
avant de laisser le Developer démarrer."
```

### Session Developer (première implémentation)
```
Instruction d'activation :
"Tu es l'agent Developer de ce projet.
Lis agents/developer.md.
Lis CLAUDE.md, glossary.md, current-task.md.
Lis les specs référencées dans current-task.md.
Implémente la tâche courante."
```

---

## Étape 5 — Rythme de Travail

```
Phase 1  →  ~3-4 tâches  →  ~4-6 sessions (Developer + Reviewer + Tester)
Phase 2  →  ~3-4 tâches
...

Chaque session = 1 rôle = 1 tâche ou 1 review ou 1 test suite

Ne JAMAIS mixer les rôles dans une même session.
```

---

## Rappel des Fichiers Clés

| Besoin | Fichier |
|---|---|
| Comprendre le vocabulaire | `.claude/glossary.md` |
| Voir où on en est | `.claude/roadmap.md` + `.claude/feature-summaries/README.md` |
| Savoir quoi faire maintenant | `.claude/current-task.md` |
| Décisions architecturales | `.claude/adr/*.md` |
| Comment les agents collaborent | `.claude/guides/agent-orchestration.md` |

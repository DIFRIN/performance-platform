# Guide — Orchestration des Agents AI

> Ce guide décrit comment coordonner les agents Developer, Reviewer, Tester et Architect
> pour produire du code production-grade avec le minimum d'allers-retours et le maximum
> de cohérence. Il est lu par l'humain qui orchestre les agents, et peut être donné
> en contexte à n'importe quel agent pour qu'il comprenne sa place dans le workflow.

---

## 1. Vue d'Ensemble du Workflow

```
┌─────────────────────────────────────────────────────┐
│            PHASE 0 — CONCEPTION (1 fois)            │
│                                                     │
│  ┌─────────────────┐    ┌────────────┐              │
│  │ SYSTEM DESIGNER │───▶│  ARCHITECT │ (escalades)  │
│  └─────────────────┘    └────────────┘              │
│    Produit PDRs + Issues dans progress.md           │
└─────────────────────────────────────────────────────┘
                       │
                       ▼ progress.md initialisé
┌─────────────────────────────────────────────────────┐
│         BOUCLE DE DÉVELOPPEMENT (par Issue)         │
│                                                     │
│  progress.md ──▶ DEVELOPER ──▶ REVIEWER ──▶ TESTER │
│  (choisit        (implémente)  (review)   (intég.)  │
│   l'Issue)            │            │                │
│       ▲    CHANGES_REQUESTED       │ FAIL           │
│       └────────────────────────────┘                │
│                                                     │
│  Chaque Issue : WAITING→IN PROGRESS→IN REVIEW→DONE  │
└─────────────────────────────────────────────────────┘
```

**Règle fondamentale** : Les agents ne se court-circuitent JAMAIS.
Le code ne passe JAMAIS de Developer directement à la phase suivante
sans avoir été APPROVED par le Reviewer.

---

## 2. Les Quatre Agents

| Agent | Fichier | Quand l'activer |
|---|---|---|
| **System Designer** | `agents/system-designer.md` | **Une fois au démarrage** — lit les specs, crée PDRs + Issues dans `progress.md` |
| **Architect** | `agents/architect.md` | Escalades du Designer/Developer, ADRs, décisions bloquantes |
| **Developer** | `agents/developer.md` | Chaque session — prend la prochaine Issue dans `progress.md` |
| **Reviewer** | `agents/reviewer.md` | Après chaque Issue IN REVIEW dans `progress.md` |
| **Tester** | `agents/tester.md` | Après APPROVED sur Issues nécessitant des tests d'intégration |

---

## 3. Comment Activer un Agent

Les prompts d'activation sont dans `prompts/`. Utiliser uniquement ces fichiers —
ils sont calibrés pour charger le minimum de contexte nécessaire.

| Agent | Prompt | Variantes disponibles |
|---|---|---|
| System Designer | `.claude/agents/system-designer.md` | Initial, partiel, revue |
| Developer | `.claude/agents/developer.md` | Standard, reprise, post-review |
| Reviewer | `.claude/agents/reviewer.md` | Standard, re-review |
| Tester | `.claude/agents/tester.md` | Standard, contrats |
| Architect | `.claude/agents/architect.md` | Validation phase, escalade, révision ADR |
| Reprise inconnue | `prompts/session-bootstrap.md` | Universel |

**Ne jamais écrire un prompt d'activation de tête** — toujours copier depuis `prompts/`.

---

## 4. Workflow Détaillé par Phase de Roadmap

### 4.1 Démarrage d'une Nouvelle Phase

**Responsable** : Architect + Humain

```
1. Humain vérifie que la phase précédente est ✅ dans roadmap.md
2. Activer Architect :
   "Valide le démarrage de la Phase X. Vérifie :
    - Prérequis de la phase précédente satisfaits (PDRs DONE dans progress.md)
    - Specs de la phase complètes et sans ambiguïté
    - Risques identifiés
    - Issues de la Phase X créées dans progress.md par le System Designer"
3. Architect produit son validation ou ses ajustements
4. System Designer crée les Issues de la Phase X dans progress.md
```

### 4.2 Cycle d'Implémentation d'une Tâche

```
┌─ DÉMARRAGE ─────────────────────────────────────────────────────┐
│ 1. Humain vérifie que progress.md a une Issue WAITING débloquée │
│ 2. Humain vérifie que l'Issue précédente est DONE dans progress │
└─────────────────────────────────────────────────────────────────┘
           │
           ▼
┌─ DÉVELOPPEMENT ─────────────────────────────────────────────────┐
│ Activer Developer                                               │
│ → Il lit progress.md + issues/ISSUE-XXX.md                     │
│ → Il implémente                                                 │
│ → Il écrit les tests unitaires                                  │
│ → Il met à jour feature-summaries/README.md                     │
│ → Il marque l'Issue IN REVIEW dans progress.md                  │
│                                                                 │
│ ⚠️ Si Developer escalade → activer Architect avant de continuer │
└─────────────────────────────────────────────────────────────────┘
           │
           ▼
┌─ REVIEW ────────────────────────────────────────────────────────┐
│ Activer Reviewer                                                │
│ → Il lit issues/ISSUE-XXX.md + pdr/PDR-XXX.md                  │
│ → Il passe toutes ses checklists                                │
│ → Il produit son rapport                                        │
│                                                                 │
│ Si APPROVED → continuer vers Tests                              │
│ Si CHANGES_REQUESTED → retour Developer (voir 4.3)             │
│ Si REJECTED → escalade Architect (voir 4.4)                    │
└─────────────────────────────────────────────────────────────────┘
           │ APPROVED
           ▼
┌─ TESTS D'INTÉGRATION ───────────────────────────────────────────┐
│ Activer Tester (si la tâche nécessite des tests d'intégration)  │
│ → Il définit son plan de test                                   │
│ → Il écrit les tests Testcontainers                             │
│ → Il exécute et vérifie                                         │
│ → Il met à jour feature-summaries/README.md                     │
│                                                                 │
│ Si tous passent → Tâche ✅ DONE                                 │
│ Si un test E2E échoue → retour Developer avec rapport précis    │
└─────────────────────────────────────────────────────────────────┘
           │
           ▼
       Tâche DONE → prochaine tâche dans roadmap.md
```

### 4.3 Cycle de Correction (CHANGES_REQUESTED)

```
Reviewer → CHANGES_REQUESTED avec liste de points bloquants

Activer Developer :
"Le Reviewer a demandé des corrections sur la tâche [X].
Voici le rapport de review : [coller le rapport]
Corrige les points BLOQUANTS listés.
Ne modifie que ce qui est demandé."

→ Developer corrige
→ Réactiver Reviewer pour re-review des points corrigés uniquement

Règle : maximum 2 cycles de CHANGES_REQUESTED.
Si 3ème cycle nécessaire → escalade Architect.
```

### 4.4 Escalade Architect

```
Activer Architect :
"Escalade depuis [Developer/Reviewer] sur la tâche [X].
Problème : [description précise]
Spec concernée : [référence]
Question : [question précise]"

→ Architect tranche
→ Met à jour la spec ou l'ADR si nécessaire
→ Developer reprend avec la décision de l'Architect
```

---

## 5. Règles de Gestion du Contexte

### Problème
Claude Code a un contexte limité. Charger tous les fichiers à chaque session
consomme des tokens inutilement.

### Règle de Chargement Minimal

Chaque agent charge UNIQUEMENT :

**Developer** :
```
session-state.md             (toujours — en premier)
progress.md                  (toujours — trouver l'Issue active)
issues/ISSUE-XXX.md          (toujours — l'Issue active)
pdr/PDR-XXX.md               (si l'Issue le référence explicitement)
agents/developer.md          (première session ou si incertitude sur les règles)
```

**Reviewer** :
```
agents/reviewer.md           (toujours)
issues/ISSUE-XXX.md          (l'Issue IN REVIEW)
pdr/PDR-XXX.md               (signatures de référence)
context/interfaces-registry.md  (statuts attendus)
```

**Tester** :
```
agents/tester.md             (section phase concernée)
issues/ISSUE-XXX.md          (composants à tester)
pdr/PDR-XXX.md               (interfaces exactes)
progress.md                  (voir quelles Issues sont DONE — éviter doublons)
```

**Architect** :
```
agents/architect.md          (toujours)
adr/*.md                     (tous - pour ne pas re-décider ce qui l'est déjà)
architecture.md              (toujours)
constraints.md               (toujours)
[spec concernée par l'escalade]
```

### Règle d'Or
> Ne jamais charger un fichier "au cas où". Si l'agent a besoin d'un fichier
> qu'il n'a pas, il demande explicitement plutôt que de le deviner.

---

## 6. État du Projet et Traçabilité

### Fichiers d'État

| Fichier | Mis à jour par | Fréquence |
|---|---|---|
| `progress.md` | System Designer + Developer + Reviewer | Chaque changement de statut |
| `feature-summaries/README.md` | Developer + Tester | Fin de chaque tâche |
| `roadmap.md` | Humain + Architect | Fin de chaque phase |
| `adr/` | Architect | Sur décision architecturale |

### Format du Suivi dans feature-summaries/README.md

```markdown
### [Phase 1] Parsing YAML ScenarioDefinition — 2026-06-10

**Agent** : Developer → Reviewer (APPROVED) → Tester  
**Modules** : `platform-domain`, `platform-scenario-dsl`  
**Fichiers clés** :
- `ScenarioDefinition.java` — record immuable, entrée principale du parser
- `YamlScenarioParser.java` — implémentation SnakeYAML → ScenarioDefinition
- `ScenarioValidator.java` — validation des règles spec-01

**Ce qui fonctionne** :
- Parse tous les types de tâches (DATABASE, SHELL, GATLING...)
- Valide les cycles dans dependsOn
- Retourne des erreurs de validation avec champ + chemin

**Limites connues** :
- Les loadModels inline dans les injections ne sont pas encore supportés (Phase 2)

**Tests** : 12 unitaires (Parser), 8 unitaires (Validator), 15 intégration (fixtures YAML)  
**Review** : APPROVED au 1er cycle  
**Cycles de correction** : 0
```

---

## 7. Séquences d'Activation Recommandées

### Démarrer le projet from scratch (Phase 1)

```
Session 1 — Architect
"Lis toutes les specs. Produis les PDRs et Issues de la Phase 1
dans progress.md. Attends validation avant d'écrire les fichiers."

Session 2 — Developer
"Implémente ISSUE-001 selon issues/ISSUE-001.md."

Session 3 — Reviewer
"Review ISSUE-001 passée IN REVIEW par le Developer."

Session 4 — Tester
"Écris les tests de contrat pour les composants de ISSUE-001 et ISSUE-002."
```

### Démarrer une tâche complexe (ex: Transport Layer)

```
Session 1 — Architect
"Valide le découpage de la Phase 7 (Transport).
Identifie les risques. Prépare current-task.md pour
l'implémentation de ExecutionTransport interface + KafkaExecutionTransport."

Session 2 — Developer
[implémentation]

Session 3 — Reviewer
[review]

Session 4 — Developer (si CHANGES_REQUESTED)
[corrections]

Session 5 — Reviewer
[re-review partielle]

Session 6 — Tester
[KafkaTransportContractTest + tests Testcontainers]
```

---

## 8. Anti-Patterns à Éviter

| Anti-pattern | Problème | Solution |
|---|---|---|
| Activer Developer sans Issue WAITING dans `progress.md` | L'agent ne peut pas choisir de tâche → blocage | System Designer doit créer les Issues d'abord |
| Court-circuiter le Reviewer | De la dette technique s'accumule sans être détectée | Reviewer obligatoire après chaque tâche Developer |
| Charger tous les fichiers dans chaque session | Consommation de tokens × 5-10 inutilement | Chargement minimal selon table de routing |
| Laisser l'Architect coder | Il perd son recul architectural | L'Architect décide, le Developer code |
| Re-discuter un ADR sans nouveau contexte | Perte de temps et incohérences | Toujours vérifier les ADRs avant une décision |
| Une Issue couvre plusieurs modules Maven | Périmètre trop large, review difficile | Une Issue = un module = une session Developer |
| Demander au Tester d'écrire des tests unitaires | Responsabilité croisée, coverage dupliqué | Tests unitaires = Developer, intégration = Tester |

---

## 9. Résolution des Conflits Entre Agents

### Developer vs Reviewer (désaccord sur une correction)
→ L'humain tranche en s'appuyant sur la spec.
→ Si la spec est ambiguë → escalade Architect.

### Reviewer vs Spec (la spec semble incorrecte)
→ Reviewer documente l'incohérence dans son rapport.
→ Humain décide : corriger la spec (ADR) ou appliquer la spec telle quelle.
→ Ne jamais "interpréter" une spec — la suivre ou la corriger officiellement.

### Tester vs Developer (test qui échoue sur comportement non spécifié)
→ Si le comportement n'est pas dans la spec : escalade Architect.
→ L'Architect décide si c'est un bug ou un comportement non spécifié à documenter.

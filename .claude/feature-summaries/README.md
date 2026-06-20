# Feature Summaries — Release Notes

> Ce fichier documente les fonctionnalités terminées et validées, pour communication
> externe (release notes, changelog). Ce n'est PAS un journal de session.
>
> Journal de session → `.claude/session-state.md`
> Avancement des Issues → `.claude/progress.md`
> Historique des statuts → `.claude/progress.md` section "Historique des Changements"
>
> Mis à jour par le Reviewer ET le Tester quand un PDR entier est DONE
> (toutes ses Issues DONE + tests d'intégration verts).

---

## Format d'Entrée

```markdown
### PDR-XXX — [Nom du composant] — [date]

**Module** : `platform-xxx`
**Issues livrées** : ISSUE-XXX, ISSUE-YYY, ISSUE-ZZZ
**Comportements disponibles** :
- [Ce qu'on peut faire avec ce composant — orienté utilisateur/intégrateur]
**Limites connues** :
- [Ce qui n'est pas encore supporté — avec référence à l'Issue future]
```

---

## Historique

### PDR-015 — Reporting Engine — 2026-06-20

**Module** : `platform-reporting`
**Issues livrées** : ISSUE-065, ISSUE-066, ISSUE-067, ISSUE-068, ISSUE-069
**Comportements disponibles** :
- Génération de CampaignReport consolidé à partir d'un ExecutionState (DefaultReportEngine)
- Classification automatique des tâches par phase (préparation/injection/assertion)
- Calcul de verdict (SUCCESS/WARNING/FAILED) avec priorité ERROR > FAILED
- Rendu HTML, PDF (via OpenHTMLToPDF) et JSON (Jackson pretty-print)
- Ecriture des rapports sur disque avec structure `<outputDir>/<executionId>/campaign.{html,pdf,json}`
- Copie parallelisée des répertoires Gatling via Virtual Threads
- Ecoute événementielle `ScenarioFinished` pour déclenchement automatique
- Publication `ReportGenerated` event après génération
- VerdictReason formatée avec compteurs passed/failed/error/skipped
- HTML escaping contre XSS
- Configuration via `@ConfigurationProperties(prefix = "reporting")`
- Support multi-agent (collecte des agentIds)
- Gestion robuste : contexte vide, tâches SKIPPED/FAILED/TIMEOUT, répertoires Gatling manquants

**Limites connues** :
- Les ReportPublishers (Confluence/S3/Git/SharePoint/Nexus) sont définis dans PDR-016 (ISSUE-070..073), non encore implémentés
- Le template HTML est chargé depuis le classpath, pas d'override externe (future amélioration)
- Les métadonnées `tags` et `metadata` du CampaignReport sont vides par défaut (pas de source dans ExecutionState actuel)

**Tests d'intégration (Tester)** :
- **Cas testés** : 27 contractuels (Renderers), 14 E2E (Pipeline complet), 21 intégration (Engine avancé) = 62 nouveaux (154 total)
- **Infrastructure** : Aucune (module purement computationnel + I/O fichiers)
- **Résultat** : 154/154 passent, BUILD SUCCESS
- **Temps d'exécution** : ~22s (suite complète)

---

### PDR-005 — Scenario DSL — 2026-06-20 (E2E Tests)

**Module** : `platform-scenario-dsl`
**Type de tests** : E2E Contract (YAML fixtures reels)
**Cas testés** : 24 E2E (8 valides, 7 invalides, 6 edge cases, 3 integration realiste)
**Infrastructure** : 10 fichiers YAML dans `src/test/resources/scenarios/`
**Resultat** : 24/24 passent. Full pipeline parser→validator→usecase valide.
**Temps d'execution** : ~6s

### PDR-006 — Execution Engine — 2026-06-20 (E2E Tests)

**Module** : `platform-execution-engine`
**Type de tests** : E2E (orchestration complete avec stubs realistes)
**Cas testés** : 14 E2E (flow multi-phase, DAG ordering, verdicts SUCCESS/FAILED, retry, cancel, phases paralleles, context propagation)
**Infrastructure** : Aucune (stubs TaskExecutor/AssertionExecutor avec latence simulee)
**Resultat** : 14/14 passent
**Temps d'execution** : ~2s

### PDR-007/008 — Transport Layer — 2026-06-20 (Contract Tests)

**Module** : `platform-transport`
**Type de tests** : Contract (ExecutionTransport contrat abstrait pour toutes implementations)
**Cas testés** : 21 contractuels (connexion:6, dispatch/receive:4, events:4, signals:2, agent lifecycle:2, edge cases:3)
**Infrastructure** : InMemoryExecutionTransport
**Resultat** : 21/21 passent
**Temps d'execution** : ~4s

### PDR-009 — Agent Runtime — 2026-06-20 (E2E Tests)

**Module** : `platform-agent-runtime`
**Type de tests** : E2E (coordination agent avec transport partage)
**Cas testés** : 7 E2E (execution single-agent:3, lifecycle:2, cleanup:1, concurrent:1)
**Infrastructure** : InMemoryExecutionTransport + DistributedAgentRuntime
**Resultat** : 7/7 passent
**Temps d'execution** : ~3s

### PDR-013 — Gatling Injection — 2026-06-20 (E2E Tests)

**Module** : `platform-injection-gatling`
**Type de tests** : E2E (pipeline injection complet avec stubs runner/parser)
**Cas testés** : 9 E2E (load models RAMP/CONSTANT/BURST/LoadModel record:4, error handling:3, cleanup:1, multi-step:1)
**Infrastructure** : Stubs GatlingRunner + GatlingResultParser
**Resultat** : 9/9 passent
**Temps d'execution** : ~1.5s

### PDR-014 — Assertion Framework — 2026-06-20 (E2E Tests)

**Module** : `platform-assertion`
**Type de tests** : E2E (pipeline d'assertion multi-executor)
**Cas testés** : 9 E2E (multi-assertion types:4, SLA checks realistes:3, edge cases:2)
**Infrastructure** : DefaultAssertionExecutorRegistry (in-memory)
**Resultat** : 9/9 passent
**Temps d'execution** : ~1s

**Total E2E/Contract Tests (Tester — 2026-06-20)** : 84 nouveaux tests repartis sur 6 modules. BUILD SUCCESS (84/84 OK).

# ISSUE-209: Modèle fermé (utilisateurs concurrents)
**Status**: APPROVED
**PDR**: PDR-056
**Module**: platform-injection-gatling
**Started**: 2026-11-17T09:52+01:00
**IssueFile**: issues/ISSUE-209-gatling-closed-model.md

> 📄 Full specification: `.claude/workspace/issues/ISSUE-209-gatling-closed-model.md`

## Reviewer Feedback
(None yet)

---
## Reviewer Feedback — 2026-11-17T09:58+01:00
1) Critère de Done non couvert : aucun test n'exécute un plan fermé (ClosedModelTranslatorTest ne teste que les classes de steps). Ajouter un test d'exécution (ex. DefaultGatlingRunnerTest avec plan CLOSED, HttpScenarioSimulation/WireMock ou simulation minimale) vérifiant que la population est maintenue / que la simulation termine via maxDuration. 2) Tests Kafka/HTTP du branchement closed (SimulationInjectionHolder.getClosed, immutabilité Closed) absents. 3) HttpScenarioSimulation.java et KafkaScenarioSimulation.java : diff de fichier entier (fins de ligne CRLF→LF changées) — restaurer les fins de ligne d'origine pour un diff lisible. 4) Mineur : accolades superflues dans le case Hold; FQN inline dans DefaultGatlingRunner (imports à ajouter). Tests module: 103 OK.

# ISSUE-208: Traducteur Gatling du plan (modèle ouvert)
**Status**: APPROVED
**PDR**: PDR-056
**Module**: platform-injection-gatling
**Started**: 2026-11-17T09:45+01:00
**IssueFile**: issues/ISSUE-208-gatling-open-plan-translator.md

> 📄 Full specification: `.claude/workspace/issues/ISSUE-208-gatling-open-plan-translator.md`

## Reviewer Feedback
(None yet)

---
## Reviewer Feedback — 2026-11-17T09:50+01:00
Code et tests OK (mvn test module vert). Critère de Done non satisfait : grep LoadModel dans infra/platform-injection-gatling/pom.xml lignes 16 (description: 'Traduction des LoadModel en OpenInjectionStep') et 19 (commentaire '(LoadModel, LoadModelType, InjectionResult)'). Remplacer par WorkloadPlan / plan de charge.

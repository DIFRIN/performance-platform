# Decisions Log

> Journal de toutes les micro-décisions prises pendant l'implémentation.
> Distincte des ADRs (décisions architecturales majeures) : ici ce sont les
> décisions de détail d'implémentation qui ne méritent pas un ADR complet
> mais qui doivent être traçables pour éviter de les re-discuter.
>
> Mis à jour par le Developer à chaque décision non-évidente.
> Lu par le Reviewer pour comprendre les choix, par l'Architect pour détecter des patterns.

---

## Format

```
[YYYY-MM-DD] [MODULE] DÉCISION : [ce qui a été décidé]
CONTEXTE : [pourquoi cette question s'est posée]
RAISON : [pourquoi ce choix et pas un autre]
IMPACT : [quels fichiers / comportements sont affectés]
```

---

## Log

[2026-06-13] [platform-domain] DECISION : ReportPublished utilise String target au lieu de PublicationTarget
CONTEXTE : PublicationTarget a ete retire du domaine par le System Designer (correction ISSUE-002 — fuite technologique : noms d'outils comme CONFLUENCE, S3). Mais PDR-002 et ISSUE-009, crees avant cette correction, referencent toujours PublicationTarget dans le record ReportPublished. Au moment d'implementer ISSUE-009, le type PublicationTarget n'existe plus dans platform-domain.
RAISON : Le domaine ne doit pas connaitre les outils de publication concrets. Un String opaque preserve la genericite de l'evenement domaine tout en permettant au module platform-reporting d'utiliser son propre enum PublicationTarget pour les valeurs concretes.
IMPACT : ReportPublished a field String target au lieu de PublicationTarget target. La Javadoc precise que les valeurs concretes sont definies dans platform-reporting.
[2026-06-13] [platform-domain] DECISION : SignalId et ReportId n'ont pas de factory of() — utilise constructeur direct dans les tests
[2026-06-14] [platform-transport] DECISION : TaskExecutionRequest, ExecutionEvent, et TransportType crees dans ISSUE-025 (et non dans les Issues designees ISSUE-026 et ISSUE-028)
CONTEXTE : L'interface ExecutionTransport (ISSUE-025) reference TaskExecutionRequest, ExecutionEvent, et TransportType dans ses signatures. Ces types etaient planifies pour ISSUE-026 (messages) et ISSUE-028 (properties). La compilation de l'interface est impossible sans ces types.
RAISON : Les types sont dans le meme module platform-transport. Les creer dans ISSUE-025 permet la compilation immediate de l'interface. ISSUE-026 pourra ajouter le test unitaire TransportMessagesTest.java. ISSUE-028 pourra ajouter les proprietes de configuration par type. Aucune duplication generee.
IMPACT : ISSUE-025 cree 10 fichiers (vs 7 prevus). ISSUE-026 herite de TaskExecutionRequest.java et ExecutionEvent.java deja crees — reduit a la creation de TransportMessagesTest.java. TransportType anciennement REMOVED du domaine est maintenant STABLE dans platform-transport.
CONTEXTE : Contrairement a AgentId et ExecutionId qui ont of(), SignalId n'a que generate() et ReportId n'a que generate(). Cette decision a ete prise dans ISSUE-001.
RAISON : Les IDs de signal et de rapport sont toujours generes (pas de valeur predefinie en test). Pour les tests, new SignalId("x") ou SignalId.generate() suffisent.
IMPACT : SignalsTest utilise new ReportId("report-1") et SignalId.generate().

[2026-06-14] [platform-execution-engine] DECISION : Kahn's BFS avec merge Math::max pour calculer les dagLevels — sans librairie externe
CONTEXTE : La question "BFS ou tri topologique ? Quelle lib ?" etait en suspens depuis la phase de design. ISSUE-019 implemente ExecutionPlanBuilder.
RAISON : Kahn (BFS sur degres entrants) est l'algorithme standard pour le tri topologique de DAG. Le merge avec Math::max gere correctement les dependances multiples (diamond pattern). Aucune librairie externe necessaire — l'algorithme tient en ~55 lignes de code et les collections Java standard suffisent.
IMPACT : DagLevelCalculator.java dans platform-execution-engine/engine/plan/. Les dagLevels sont calcules globalement (toutes phases confondues) pour garantir la coherence des niveaux en presence de dependances inter-phases.

---

[2026-06-14] [platform-transport + platform-agent-runtime] DECISION : AgentLifecycleEvent séparé de ExecutionEvent (ADR-012)
CONTEXTE : `TransportAgentRegistration` utilisait un sentinel `ExecutionId.of("NO_EXECUTION")` pour les events d'enregistrement/heartbeat car `ExecutionEvent` requiert un `executionId` obligatoire. Identifié comme design smell lors de la revue architecturale post-ISSUE-034/035.
RAISON : Option B retenue (sur 3 options évaluées) — créer `AgentLifecycleEvent` sans `executionId` et étendre `ExecutionTransport` avec `publishAgentEvent()` + `subscribeAgentEvents()`. Option A (sentinel) rejetée pour sémantique incorrecte. Option C (executionId nullable) rejetée car modifie le contrat de `ExecutionEvent` et force gestion null sur tous les handlers existants.
IMPACT : Nouveaux fichiers `AgentLifecycleEvent.java` et `AgentLifecycleEventHandler.java` dans `platform-transport`. `ExecutionTransport` étendu (⚡ interface publique critique). `TransportAgentRegistration` migre de `publishEvent` vers `publishAgentEvent`. Voir ADR-012.

---

## Décisions Attendues (À Ne Pas Oublier)

Ces points sont connus comme source de micro-décisions à prendre lors de l'implémentation.
Documenter la décision prise ici quand elle est tranchée.

| Sujet | Phase | Question |
|---|---|---|
| `ExecutionContext` serialization | Phase 1 | Comment sérialiser les `Object` du store pour le transport ? |
| ~~DAG level computation~~ | Phase 2 | Resolu : Kahn's BFS, pas de lib (ISSUE-019) |
| Gatling in-process isolation | Phase 4 | Comment isoler le classloader Gatling du reste de l'app ? |
| Agent heartbeat storage | Phase 7 | In-memory (perte sur restart) ou PostgreSQL ? |
| Report HTML templating | Phase 6 | Thymeleaf ou template string pur ? |
| TaskExecutionRequest serialization | Phase 7 | Jackson (décidé : format Anthropic-compatible JSON) |

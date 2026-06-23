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

---

## Decisions ISSUE-082

[2026-06-20] [platform-app] DECISION: @SpringBootTest contourne par manual wiring + Testcontainers
CONTEXTE: @SpringBootTest incompatible (SpringExtension.getTestContextManager() appelle
computeIfAbsent, methode supprimee de JUnit 5.11.4 ExtensionContext$Store).
RAISON: Le contournement via manual wiring est deja etabli dans le codebase
(ExecutionEngineE2ETest, ReportingPipelineE2ETest, EntitiesMappingIT).
IMPACT: LocalFlowE2ETest utilise Hibernate SessionFactory + Flyway + PostgreSQL
Testcontainer directement, sans Spring Data JPA. RawJpaExecutionRepository
wrapper JPA/Hibernate pur implementant ExecutionRepository.

[2026-06-20] [platform-app] DECISION: Injection shell plutot que Gatling dans l'E2E
CONTEXTE: Gatling demande une classe Simulation compilee sur le classpath, un
classloader isole, et un serveur HTTP cible. Trop de complexite pour un test E2E.
RAISON: Le test doit verifier le flux complet (PREP->INJECT->ASSERT), pas specifiquement
Gatling. ShellTaskExecutor est plus fiable, cross-platform (echo), et sans dependance externe.
IMPACT: e2e-local.yaml utilise "shell" avec "echo" pour la phase INJECTION au lieu de "gatling".

[2026-06-20] [platform-app] DECISION: E2E test execute via surefire (pas failsafe)
CONTEXTE: Failsafe (integration-test) echoue apres spring-boot:repackage (package phase)
avec NoClassDefFound sur ScenarioController. Le test passe correctement via surefire
et aussi via failsafe:integration-test isole.
RAISON: Le test est quand meme execute pendant mvn verify (phase test avant integration-test).
Le profil integration-tests active failsafe pour les *IT.java, pas pour les *E2ETest.
IMPACT: LocalFlowE2ETest.java execute par surefire. mvn verify -P integration-tests OK.

---

## Decisions ADR PDR-020..024 (revue Architect 2026-06-21)

[2026-06-21] [architecture] DECISION : ADR-015 — Named Resource Registry Pattern officialise comme standard
CONTEXTE : PDR-020 (Kafka) et PDR-022 (HTTP) repliquent le pattern DatasourceProvider/ADR-014 mot pour mot. Question : ADR-014 (datasource only) suffit-il pour les ressources externes futures ?
RAISON : ADR-014 n'a pas l'autorite normative transverse. Sans standard, chaque ressource future reinventerait sa convention (risque credentials inline, cles YAML incoherentes). 5 invariants : reference logique dans scenario, config sous platform.<famille>.*, credentials par env var, binding @ConfigurationProperties+Map record immuable, Registry via @Bean (pas @Component).
IMPACT : ADR-014 devient une instance du standard. S'applique a PDR-020, PDR-022, et toute ressource externe future (gRPC, S3, Redis...). Ne s'applique JAMAIS a transport.* (ADR-017) ni a platform-domain/plugin-api.

[2026-06-21] [architecture] DECISION : ADR-016 — Resolution noms logiques (topics/paths) avec fallback transparent
CONTEXTE : PDR-020 (resolveTopic) et PDR-022 (resolvePath) ajoutent un 2e niveau de resolution (sous-ressource) non couvert par ADR-014. Quel comportement si nom logique non mappe ?
RAISON : Decision nouvelle. Fallback "as-is" (retourne logicalName tel quel) garantit retrocompat + souplesse (chemins ad hoc type /__admin/...). Pas d'echec sur non-mappe : validation deleguee a l'execution (broker/serveur). Log DEBUG sur fallback pour tracabilite.
IMPACT : resolveTopic/resolvePath uniformes Kafka+HTTP. Versioning d'API sans toucher au scenario. Risque typo masquee (atténue par log DEBUG). Recommandation : noms logiques en mode nominal dans les scenarios.

[2026-06-21] [architecture] DECISION : ADR-017 — Separation transport interne vs ciblage ressources externes
CONTEXTE : PDR-021 migre le transport interne vers Spring Kafka EN MEME TEMPS que PDR-020 introduit Spring Kafka pour le ciblage SUT. Risque eleve de collision de beans et confusion Developer.
RAISON : Deux concerns distincts : transport (orchestrateur<->agents, platform-transport, transport.*, @ConditionalOnProperty, byte[], groupId=agentId ADR-009) vs ciblage SUT (platform-infrastructure, platform.*, Registry, String, groupId=config). Beans transport qualifies transport* + conditionnels. Aucun bean partage entre les deux.
IMPACT : PDR-021 beans transportKafkaTemplate/transportProducerFactory/transportContainerFactory. Executors de ciblage injectent UNIQUEMENT les registries, jamais un bean transport*. A lier dans CLAUDE.md table de routing.

[2026-06-21] [architecture] DECISION : ADR-018 — Isolation des services SUT d'exemple (platform-examples)
CONTEXTE : PDR-023 cree iot-dispatcher/device-api en Spring Boot 3.4.x, hors pom racine, sans archi hexagonale. Tension apparente avec stack NON NEGOCIABLE (CLAUDE.md §4) et regles archi.
RAISON : Le SUT n'est PAS le produit : c'est un systeme tiers simule pour la demo. Les regles plateforme (stack 4.x, hexagonale, domaine pur, registries, ArchUnit) NE s'appliquent PAS a platform-examples/. Hors build Maven racine, packages com.performance.examples.*, couplage uniquement par contrat reseau (topics/endpoints via registries ADR-015).
IMPACT : Reviewer NE doit PAS signaler les "violations" archi dans platform-examples/. NE PAS ajouter au pom racine, NE PAS upgrader vers 4.x. Build SUT verifie separement (Done PDR-023).

---

## Decisions revue Web IHM (PDR-027/028/029 — Architect 2026-06-23)

[2026-06-23] [architecture] DECISION : ADR-019 — Securite IHM = `platform.security.enabled` existant, PAS de `security.jwt.enabled`
CONTEXTE : PDR-028/ISSUE-125 introduisaient une propriete `security.jwt.enabled` independante du mode pour activer JWT. Or `SecurityConfiguration` (ISSUE-081) pilote DEJA la securite via `platform.security.enabled` + detection issuer-uri OAuth2. `security.jwt.enabled` n'existe nulle part.
RAISON : Eviter deux mecanismes concurrents et une property morte. Reutiliser l'existant ; ajouter `/`, `/index.html`, `/assets/**` aux matchers permitAll() quand la securite est active (assets publics, pas de login v1). `platform-app` reste l'adapter entrant racine (serving statique + controllers + use cases = pas une violation Modulith).
IMPACT : ISSUE-125 corrigee (cle securite + matchers statiques). PDR-028 a aligner. SecurityConfiguration.java a etendre. Voir ADR-019.

[2026-06-23] [architecture] DECISION : ADR-020 — Read-model executions : progress (state+taskResults), pas de port CQRS separe, delete interdit si actif
CONTEXTE : ISSUE-120 prevoyait `ExecutionProgressCalculator` derivant la progression d'un `ExecutionState` SEUL. Or `ExecutionState` ne porte PAS les resultats par task (lus via `getTaskResults`). Question CQRS : port de query separe ? Et delete d'une execution active.
RAISON : Calcul impossible sans `taskResults` → signature corrigee `calculate(state, Map<TaskId, Map<AgentId, TaskResult>>)`. Port unique conserve (pas de read-store distinct en v1). Delete sur STARTED/RUNNING interdit (protege checkpointing CNF-02, courses multi-claim ADR-011) → `ExecutionNotDeletableException` → HTTP 409. `ExecutionProgress` reste un VO domaine pur.
IMPACT : ISSUE-119/120/121 corrigees. PDR-027 a aligner. Voir ADR-020.

[2026-06-23] [architecture] CLARIFICATION : modes d'acces (API/IHM/CLI) reserves a l'orchestrateur — AGENT n'expose rien
CONTEXTE : Clarification utilisateur d'une contrainte d'acces fondamentale. En mode DISTRIBUTED, seul l'ORCHESTRATOR expose les modes d'acces (API/IHM/CLI). Les AGENTS n'exposent rien. En LOCAL, tout est disponible (orchestrateur et agent dans la meme JVM). La matrice : LOCAL=tout, ORCHESTRATOR=tout, AGENT=rien (ni API, ni IHM, ni CLI, ni Tomcat ; uniquement connexions sortantes au transport).
RAISON : Un agent est un pur worker pilote par le transport, pas un point d'entree. La garantie en mode AGENT est plus forte que "UI desactivee" : c'est `WebApplicationType.NONE` force au demarrage (aucun serveur web). Decide dans `main()` (meme mecanisme que le CLI headless). Formalisation d'une contrainte existante, pas de nouveau scope — aucun PDR/Issue cree.
IMPACT : ADR-019 (decision 4 ajoutee + matrice mode runtime x mode d'acces). ADR-021 (decision 4 : CLI reserve LOCAL/ORCHESTRATOR, CliScenarioRunner jamais actif en AGENT). ISSUE-125 (regle + Done : AGENT = WebApplicationType.NONE, pas seulement web.ui.enabled=false). ISSUE-131 (garde : CliScenarioRunner jamais instancie en AGENT). PDR-028 (modes d'acces + Done alignes). specs/00-overview.md (matrice canonique). specs/04-agent-runtime.md (note "aucune surface d'acces en AGENT"). CLAUDE.md section 8 (3 lignes de routing). NB distinction preservee : le recepteur de transport HTTP entrant (agent.http.callbackUrl, spec 04 §8, si transport.type=HTTP) n'est PAS une surface d'acces API/IHM/CLI.

[2026-06-23] [architecture] CONSTAT : numerotation ADR — collision preexistante + headless inexistant
CONTEXTE : Deux fichiers ADR-015 coexistent (named-resource-registry-pattern ET configuration-driven-agent-specialization) ; le decisions-log reference ADR-015/016/017 avec des titres differents des fichiers ADR-016/017/018 sur disque. Le plus grand numero de FICHIER est 018. Les nouveaux ADRs prennent donc 019 et 020 (la demande d'un "ADR-016" est impossible : numero deja pris).
RAISON : Eviter d'aggraver la collision. A nettoyer dans une passe de renumerotation dediee.
IMPACT : ADR-019 et ADR-020 crees. Par ailleurs : le mode "headless run-and-exit sur --scenario=" presente par PDR-028 comme "existant (PDR-018)" N'EXISTE PAS dans `PerformancePlatformApplication`. Critere retire d'ISSUE-125 ; Issue dediee + ADR requis si ce mode est voulu.

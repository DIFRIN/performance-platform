# Recommendations Tracking

> Fichier de suivi des recommandations non-bloquantes émises par le Reviewer,
> et des corrections architecturales imposées par l'Architect.
> **Lu par le Developer** avant de considérer une Issue comme terminée.
> **Mis à jour par le Reviewer** au moment de l'APPROVED initial.
> **Vidé par le Developer** après application + re-review confirmée.

---

## Workflow

```
Reviewer: APPROVED avec recommandations
  → écrit les recommandations ici avec statut PENDING
  → n'ajoute PAS le commit (en attente de révision)

Architect: review architecturale
  → écrit les corrections [ARCH-XX] avec statut PENDING
  → indique l'Issue cible et la priorité (BLOQUANT avant ISSUE-036 / MINEUR)

Developer: lit ce fichier, applique les recommandations
  → passe les recommandations en APPLIED
  → demande re-review (@reviewer rereview)

Reviewer: re-review confirme les corrections
  → passe en CONFIRMED
  → exécute le commit (git add + git commit)
```

---

## ⚡ Corrections Architecturales — À Appliquer AVANT ISSUE-036

> Émises par l'Architect le 2026-06-14 après revue de ISSUE-027, 033, 034, 035.
> Les items [ARCH-01] à [ARCH-05] sont BLOQUANTS pour ISSUE-036.
> Les items [ARCH-06] à [ARCH-12] sont des améliorations qualité à appliquer dans la foulée.

---

### 🔴 Critiques — BLOQUANTS avant ISSUE-036

**[ARCH-01]** [2026-06-14] [CONFIRMED] [CONCURRENCE-CRITIQUE]
`HeartbeatScheduler.sendHeartbeat()` — `Thread.currentThread().interrupt()` sur une
`RegistrationException` est sémantiquement incorrect et peut stopper silencieusement le
scheduler Virtual Thread.

**Fichier** : `platform-agent-runtime/.../registration/HeartbeatScheduler.java` lignes 130-133

**Correction** :
```java
// AVANT (incorrect)
} catch (RegistrationException e) {
    Thread.currentThread().interrupt();
}

// APRÈS (correct)
} catch (RegistrationException e) {
    log.warn("action=heartbeat_failed agentId={} will retry on next interval",
             agentId.value(), e);
    // Pas d'interruption — le schedule continue naturellement
}
```

---

**[ARCH-02]** [2026-06-14] [CONFIRMED] [CONCURRENCE-CRITIQUE]
Race condition TOCTOU dans `AgentTtlMonitor.checkExpired()` : entre `findExpired()` et
`onAgentExpired()`, un heartbeat peut rafraîchir un agent qui sera quand même supprimé.
Un agent vivant peut être retiré du registre.

**Fichiers** :
- `platform-agent-runtime/.../registry/AgentTtlMonitor.java` lignes 88-94
- `platform-agent-runtime/.../registry/InMemoryAgentRegistry.java`

**Correction** :
1. Ajouter une méthode `onAgentExpiredIfStillExpired(AgentId, Instant checkedAt)` dans
   `InMemoryAgentRegistry` qui utilise `computeIfPresent` avec re-vérification atomique :
```java
void onAgentExpiredIfStillExpired(AgentId agentId, Instant checkedAt) {
    agents.computeIfPresent(agentId, (id, existing) ->
            isExpired(existing, checkedAt) ? null : existing);
}
```
2. Modifier `AgentTtlMonitor.checkExpired()` pour appeler cette méthode au lieu de
   `onAgentExpired()` directement.

---

**[ARCH-03]** [2026-06-14] [CONFIRMED] [RESSOURCE-CRITIQUE]
`ScheduledExecutorService` non fermé dans `stop()` des deux schedulers — resource leak.
`f.cancel(false)` annule la future tâche mais NE ferme PAS le thread sous-jacent.

**Fichiers** :
- `platform-agent-runtime/.../registration/HeartbeatScheduler.java` — méthode `stop()`
- `platform-agent-runtime/.../registry/AgentTtlMonitor.java` — méthode `stop()`

**Correction** : ajouter `scheduler.shutdown()` après `f.cancel(false)` dans les deux `stop()`.

---

### 🟠 Importants — À corriger avant ISSUE-036

**[ARCH-04]** [2026-06-14] [CONFIRMED] [DESIGN-DIP]
`AgentTtlMonitor` dépend de `InMemoryAgentRegistry` (classe concrète) — violation du
Dependency Inversion Principle. Bloque les futures implémentations (Redis, JDBC) du registre.

**Fichier** : `platform-agent-runtime/.../registry/AgentTtlMonitor.java` ligne 28

**Correction** : Introduire une interface package-private `TtlTrackable` dans le package
`registry` exposant `findExpired(Instant)` et `onAgentExpiredIfStillExpired(AgentId, Instant)`.
`InMemoryAgentRegistry` implements `AgentRegistry, TtlTrackable`.
`AgentTtlMonitor` dépend de `TtlTrackable` uniquement.

```java
// package-private — visible uniquement dans le package registry
interface TtlTrackable {
    List<AgentDescriptor> findExpired(Instant now);
    void onAgentExpiredIfStillExpired(AgentId agentId, Instant checkedAt);
}
```

---

**[ARCH-05]** [2026-06-14] [CONFIRMED] [DESIGN-STATEFUL]
`HeartbeatScheduler` : `activeTaskCount` et `AgentState` figés à la construction.
Tous les heartbeats reportent le même état initial — l'orchestrateur voit toujours
`IDLE` avec 0 tâches actives, indépendamment de la réalité.

**Fichier** : `platform-agent-runtime/.../registration/HeartbeatScheduler.java`

**Correction** : Remplacer `int activeTaskCount` par des `Supplier<AgentState>` et
`Supplier<Integer>` permettant à `DistributedAgentRuntime` (ISSUE-036) de passer des
lambdas reflétant l'état réel :

```java
public HeartbeatScheduler(AgentRegistrationPort registrationPort,
                          AgentId agentId,
                          int intervalSeconds,
                          Supplier<AgentState> stateSupplier,
                          Supplier<Integer> activeTasksSupplier) { ... }

// sendHeartbeat() devient :
private void sendHeartbeat() {
    var heartbeat = new AgentHeartbeat(
            agentId,
            stateSupplier.get(),       // état réel à l'instant T
            activeTasksSupplier.get(), // tâches actives réelles
            Instant.now()
    );
    ...
}
```

Adapter les tests en conséquence (ex: `() -> AgentState.IDLE`, `() -> 0`).

---

### 🟡 Mineurs — Qualité (à appliquer dans la même PR)

**[ARCH-06]** [2026-06-14] [CONFIRMED] [ADR-012]
Migrer `TransportAgentRegistration` de `publishEvent(ExecutionEvent)` vers
`publishAgentEvent(AgentLifecycleEvent)` — suppression du sentinel `NO_EXECUTION`.

Voir **ADR-012** pour le design complet de `AgentLifecycleEvent` et l'extension de
`ExecutionTransport`. Fichiers à créer/modifier :
- CRÉER `platform-transport/.../AgentLifecycleEvent.java` (record)
- CRÉER `platform-transport/.../AgentLifecycleEventHandler.java` (functional interface)
- MODIFIER `platform-transport/.../ExecutionTransport.java` (+ `publishAgentEvent` + `subscribeAgentEvents`)
- MODIFIER `platform-transport/.../inmemory/InMemoryExecutionTransport.java`
- MODIFIER `platform-agent-runtime/.../registration/TransportAgentRegistration.java`
- MODIFIER `.claude/context/interfaces-registry.md` (ajout des nouveaux types)

---

**[ARCH-07]** [2026-06-14] [CONFIRMED] [CONTRAT]
`AgentRegistrationPort` : déclaration `throws RegistrationException` incohérente.
`register()` la déclare, `deregister()` et `sendHeartbeat()` non — bien que
l'implémentation les lève dans les 3 cas.

**Fichier** : `platform-agent-runtime/.../registration/AgentRegistrationPort.java`

**Correction** : Ajouter `throws RegistrationException` sur `deregister()` et `sendHeartbeat()`.
(`RegistrationException` est unchecked — c'est une déclaration documentaire, pas fonctionnelle.)

---

**[ARCH-08]** [2026-06-14] [CONFIRMED] [JAVADOC]
`ExecutionEvent.of()` : Javadoc incorrecte — indique "sans defensive copy" alors que la
méthode appelle le constructeur canonique qui effectue `Map.copyOf()`.

**Fichier** : `platform-transport/.../message/ExecutionEvent.java`

**Correction** : Corriger la Javadoc pour refléter le comportement réel.

---

**[ARCH-09]** [2026-06-14] [CONFIRMED] [STYLE]
`TransportAgentRegistration.capabilitiesToPayload()` utilise un nom qualifié complet
`com.performance.platform.domain.agent.AgentCapabilities` au lieu d'un import.

**Fichier** : `platform-agent-runtime/.../registration/TransportAgentRegistration.java` lignes 134-139

**Correction** : Ajouter `import com.performance.platform.domain.agent.AgentCapabilities;`
en haut du fichier et simplifier la signature de méthode.

---

**[ARCH-10]** [2026-06-14] [CONFIRMED] [LOGGING]
`AgentTtlMonitor.checkExpired()` : aucun log produit quand un agent expire. L'expiration
d'un agent est un événement opérationnel critique.

**Fichier** : `platform-agent-runtime/.../registry/AgentTtlMonitor.java` méthode `checkExpired()`

**Correction** :
```java
log.warn("action=agent_expired agentId={} lastHeartbeat={} ttl={}",
         agent.id().value(), agent.lastHeartbeatAt(), agent.registrationTtl());
```

---

**[ARCH-11]** [2026-06-14] [CONFIRMED] [ROBUSTESSE]
`InMemoryExecutionTransport` : `dispatchTask()`, `publishEvent()`, `broadcastSignal()`
opèrent sans vérifier `isConnected()`. Comportement divergent avec les implémentations
réelles (Kafka, RabbitMQ).

**Fichier** : `platform-transport/.../inmemory/InMemoryExecutionTransport.java`

**Correction** : Ajouter une vérification défensive au début de chaque méthode de dispatch :
```java
if (!connected.get()) {
    throw new TransportException("transport is not connected");
}
```

---

**[ARCH-12]** [2026-06-14] [CONFIRMED] [DETTE-TECHNIQUE]
`TransportAgentRegistration.toPayload(AgentDescriptor)` : mapping manuel champ par champ.
Si `AgentDescriptor` évolue, le payload ne sera pas mis à jour automatiquement.

**Fichier** : `platform-agent-runtime/.../registration/TransportAgentRegistration.java`

**Workaround** : Ajouter un commentaire `// NOTE: synchroniser avec AgentDescriptor si le record évolue`
au-dessus de la méthode. La solution complète (Jackson mapper) viendra avec ISSUE-028.

---

## Recommandations en Attente (Reviewer)

> Recommandations non-bloquantes du Reviewer à appliquer avant commit.

[ISSUE-035] [2026-06-14] [APPLIED] [CRAFT-07] Logging structure SLF4J absent de InMemoryAgentRegistry et AgentTtlMonitor
[ISSUE-035] [2026-06-14] [APPLIED] [TEST-06] Thread.sleep() remplacer par Awaitility dans InMemoryAgentRegistryTest
[ISSUE-035] [2026-06-14] [APPLIED] [SPEC] Annotations Spring @Component/@ConditionalOnProperty manquantes sur InMemoryAgentRegistry
[ISSUE-035] [2026-06-14] [APPLIED] [SPEC-04] AgentTtlMonitor ne publie pas l'event AgentLost
[ISSUE-036] [2026-06-15] [APPLIED] [CRAFT-05] DistributedAgentRuntime 684 lignes (>300) → 462 lignes — extraction TaskExecutionPipeline (274) + ScenarioRestartHandler (127) dans ISSUE-037
[ISSUE-036] [2026-06-15] [CONFIRMED] [CRAFT-08] Magic string "_partial_" dans toExecutionContext() → extraite en constante PARTIAL_TASK_WRAPPER
[ISSUE-036] [2026-06-15] [CONFIRMED] [TEST-06] Deux Thread.sleep() résiduels → remplacés par await().pollDelay()
[ISSUE-036] [2026-06-15] [CONFIRMED] [IMPORT-01] Import inutilisé TaskStatus supprimé
[ISSUE-036] [2026-06-15] [DEFERRED→ISSUE-039] [DESIGN] toExecutionContext() Partial→ExecutionContext — workaround à formaliser dans ISSUE-039
[ISSUE-039] [2026-06-15] [CONFIRMED] [CRAFT-02/CRAFT-08] getSupportedTaskNames() retourne Collections.unmodifiableSet (vue muable) au lieu de Set.copyOf (snapshot immuable) — Javadoc incorrecte
[ISSUE-039] [2026-06-15] [CONFIRMED] [CRAFT-01] UnsupportedTaskTypeException contient "TaskType" (terme anti-glossaire) → renommer en UnsupportedTaskNameException
[ISSUE-041] [2026-06-15] [CONFIRMED] [CRAFT-05] 6 methodes >40 lignes (execute/executeConsume/executeCount/execute/executeProduce) + KafkaConsumerTaskExecutor 322 lignes >300 — CC-02 justification + extraction pollMessages/sendMessages/sumPartitionOffsets
[ISSUE-041] [2026-06-15] [CONFIRMED] [CRAFT-07] executionId absent des logs dans KafkaConsumerTaskExecutor et KafkaProducerTaskExecutor malgre ExecutionContext disponible
[ISSUE-041] [2026-06-15] [CONFIRMED] [PRECISION] executeConsume() : toTake plafonne le compteur mais le KafkaConsumer a deja consomme tout le batch — semantique trompeuse (max.poll.records ou Javadoc)
[ISSUE-041] [2026-06-15] [CONFIRMED] [CRAFT-08] Cles de sortie ("messagesConsumed", "lag", "messagesProduced", "messagesFailed") en string literals sans constantes
[ISSUE-042] [2026-06-16] [CONFIRMED] [CRAFT-05] MockServerTaskExecutor 383 lignes > 300 — CC-02 justification ajoutee en Javadoc
[ISSUE-042] [2026-06-16] [CONFIRMED] [CRAFT-08] Cles de parametres ("deployment", "action", "port", "mappingsPath", "externalUrl") → constantes PARAM_DEPLOYMENT, PARAM_ACTION, PARAM_PORT, PARAM_MAPPINGS_PATH, PARAM_EXTERNAL_URL
[ISSUE-042] [2026-06-16] [CONFIRMED] [CRAFT-08] Magic string "default" → constante DEFAULT_EXECUTION_KEY
[ISSUE-042] [2026-06-16] [CONFIRMED] [CRAFT-07] executionId ajoute aux logs mode EXTERNAL + handler erreur execute()
[ISSUE-043] [2026-06-16] [CONFIRMED] [CRAFT-05] CC-02 justification Javadoc pour classe 355 lignes >300 + extraction executeProcess() 83L >40 (stream reading block) + execute() 56L >40
[ISSUE-043] [2026-06-16] [CONFIRMED] [TEST-06] 2 Thread.sleep(500) dans tests cleanup — remplacer par Awaitility ou CountDownLatch
[ISSUE-045] [2026-06-19] [CONFIRMED] [PRECISION-01] pathsByExecution non alimenté — extraction executionKey dans execute() + tracking dans executeCreate()/executeUpload()
[ISSUE-045] [2026-06-19] [CONFIRMED] [CRAFT-07] executionId absent des logs — ajoute executionKey a tous les log.info/log.error
[ISSUE-046] [2026-06-19] [CONFIRMED] [CRAFT-05] 3 methodes >40L (load/loadExecutorsFromJar/tryLoadClass) sans justification CC-02 — ajouter commentaire Javadoc
[ISSUE-047] [2026-06-19] [CONFIRMED] [CRAFT-05] Constructeur DefaultPluginRegistry 58 lignes (>40) sans justification CC-02 — ajouter commentaire Javadoc expliquant le flux d'initialisation cohesif
[ISSUE-049] [2026-06-19] [OVERRIDDEN] [PRECISION-02] Override <release>23</release> inutile dans platform-infrastructure/pom.xml — ArchUnit 1.3.0 gere le bytecode Java 25 sans probleme (verifie experimentalement). Supprimer le bloc <configuration><release>23</release></configuration> du maven-compiler-plugin.
[ISSUE-050] [2026-06-19] [CORRECTION] PRECISION-02 ANNULE — analyse ASM : ArchUnit 1.3.0 bundle ASM 9.7.x (Opcodes V23 max = class 67). Java 25 produit class version 69, non supporte. Release 23 est necessaire. Retabli dans ISSUE-050 avec justification documentee.
[ISSUE-052] [2026-06-19] [CONFIRMED] [CRAFT-01] Javadoc JpaExecutionRepository ligne 29-30 : "All public methods are transactional" incorrect — @Transactional retire intentionnellement (proxy Spring 7.0). Les transactions sont deleguees aux repos Spring Data sous-jacents, chaque appel etant atomique individuellement. Remplacer par Javadoc expliquant la delegation transactionnelle. Fichier : JpaExecutionRepository.java:29-30.
[ISSUE-029] [2026-06-19] [CONFIRMED] [CRAFT-08] Magic string "@type" repete 5 fois dans KafkaMessageCodec (lignes 75, 89, 106, 134, 147). Extraire en constante privee TYPE_FIELD = "@type". Fichier : platform-transport/.../kafka/KafkaMessageCodec.java.
[ISSUE-029] [2026-06-19] [CONFIRMED] [TEST-04] Absence de tests unitaires pour les null-checks de parametres publics de KafkaExecutionTransport. Ajouter 2-3 cas shouldThrowOnNull*(Request/Handler). Fichier : platform-transport/.../kafka/KafkaExecutionTransportIT.java.
[ISSUE-032] [2026-06-19] [CONFIRMED] [PRECISION-01] Javadoc obsoleted dans TransportConfiguration — corrige : "implementations completes (ISSUE-029, 030, 031, 032)". Fichier : platform-transport/.../config/TransportConfiguration.java.
[ISSUE-032] [2026-06-19] [CONFIRMED] [TEST-06] 3 Thread.sleep(200) dans SocketExecutionTransportTest → remplaces par Awaitility await().atMost(...).pollDelay(...). Fichier : platform-transport/.../socket/SocketExecutionTransportTest.java.
[ISSUE-055] [2026-06-20] [CONFIRMED] [CRAFT-05] DefaultGatlingRunner.run() 50 lignes > 40 — CC-02 Javadoc ajoute expliquant le pipeline sequentiel coherent. Fichier : .../runner/DefaultGatlingRunner.java.
[ISSUE-055] [2026-06-20] [CONFIRMED] [SPEC-01] GatlingRunner.run() — throws GatlingExecutionException ajoute a la signature. Fichier : .../runner/GatlingRunner.java.
[ISSUE-055] [2026-06-20] [CONFIRMED] [ROBUSTNESS-01] Proprietes systeme restaurees dans finally (sauvegarde + restauration). Fichier : .../runner/DefaultGatlingRunner.java.
[ISSUE-055] [2026-06-20] [CONFIRMED] [TEST-04] shouldThrowOnInvalidSimulationClass renforce — verifie GatlingExecutionException levee. Fichier : .../runner/DefaultGatlingRunnerTest.java.
[ISSUE-056] [2026-06-20] [CONFIRMED] [CRAFT-05] parse() 72 lignes > 40 sans justification CC-02 — Javadoc CC-02 ajoutee expliquant le pipeline sequentiel cohesif (find→read→extract→rawStats→assemble). Fichier : .../result/DefaultGatlingResultParser.java:57-65.
[ISSUE-057] [2026-06-20] [CONFIRMED] [CRAFT-05] GatlingTaskExecutor 330 lignes > 300 sans marqueur CC-02 dans la Javadoc de classe. Le pipeline extract→build→run→parse→assemble, les helpers de parametres, et le contrat StatefulResourceCleaner forment un ensemble cohesif inseparable. Ajouter une ligne CC-02 explicite dans la Javadoc de classe. Fichier : .../GatlingTaskExecutor.java (classe).
[ISSUE-054] [2026-06-20] [CONFIRMED] [CRAFT-05] translateCustom() 41 lignes > 40 — CC-02 ajoute en prefixe Javadoc (pipeline cohesif: validation points, iteration segments, construction ramps). Fichier : .../load/DefaultLoadModelTranslator.java:258.
[ISSUE-059] [2026-06-20] [CONFIRMED] [SPEC-01] pom.xml — dependance platform-injection-gatling deferred a ISSUE-060 (GatlingMetricAssertionExecutor). Confirme par Developer.
[ISSUE-060] [2026-06-20] [CONFIRMED] [CRAFT-07] executionId ajoute aux logs structures (log.info ligne 119 + log.warn ligne 133). executionId=context.executionId().value(). Fichier : .../assertion/gatling/GatlingMetricAssertionExecutor.java.
[ISSUE-060] [2026-06-20] [CONFIRMED] [PRECISION] Import inutilise TaskStatus supprime. Fichier : .../assertion/gatling/GatlingMetricAssertionExecutorTest.java.

---

[ISSUE-061] [2026-06-20] [CONFIRMED] [TEST-04] Test shouldErrorOnEmptyResult corrige — requete non-agregee (SELECT value FROM metrics) sur table vide apres DELETE couvre bien le chemin rs.next() == false. 54 tests OK. Fichier : .../assertion/database/DatabaseAssertionExecutorIT.java.

[ISSUE-062] [2026-06-20] [CONFIRMED] [CRAFT-05] evaluate() 93 lignes (>40) avec CC-02 en Javadoc de classe uniquement. CC-02 deplace de la Javadoc classe vers la Javadoc methode evaluate() — conforme au pattern ISSUE-055/056/057. 63 tests OK. Fichier : .../assertion/kafka/KafkaAssertionExecutor.java.

[ISSUE-064] [2026-06-20] [CONFIRMED] [CRAFT-05] Classe 306 lignes (>300) sans marqueur CC-02 dans Javadoc classe. Les 5 checks + helpers parametres + SHA-256 + builders resultat forment un ensemble cohesif inseparable. Ajouter ligne CC-02 dans la Javadoc de classe. Fichier : .../assertion/file/FileAssertionExecutor.java (classe).

[ISSUE-064] [2026-06-20] [CONFIRMED] [CRAFT-05] evaluateChecksum() 53 lignes (>40) sans marqueur CC-02 dans Javadoc. Pipeline cohesif : verification existence → extraction/validation checksum → calcul SHA-256 → comparaison → construction resultat. Ajouter Javadoc CC-02. Fichier : .../assertion/file/FileAssertionExecutor.java:165-217.

[ISSUE-064] [2026-06-20] [CONFIRMED] [CRAFT-05] evaluateSize() 41 lignes (>40) sans marqueur CC-02 dans Javadoc. Pipeline cohesif : verification existence → extraction sizeBytes → lecture taille fichier → evaluation operateur → construction resultat. Ajouter Javadoc CC-02. Fichier : .../assertion/file/FileAssertionExecutor.java:222-262.

[ISSUE-064] [2026-06-20] [CONFIRMED] [TEST-04] Chemin expectedHex.isBlank() (ligne 184) non couvert — pas de test pour checksum="sha256:" (prefix avec hex vide). Ajouter shouldErrorOnBlankChecksumHex dans ChecksumCheck. Fichier : .../assertion/file/FileAssertionExecutorTest.java.

[ISSUE-064] [2026-06-20] [CONFIRMED] [TEST-04] Chemin getRequiredDoubleParam type check (ligne 310-316) non couvert — pas de test pour sizeBytes non-numerique (ex: String "not-a-number"). Ajouter shouldErrorOnNonNumericSizeBytes dans SizeCheck. Fichier : .../assertion/file/FileAssertionExecutorTest.java.

[ISSUE-066] [2026-06-20] [CONFIRMED] [CRAFT-05] Classe DefaultReportEngine 329 lignes (>300) — CC-02 ajoute dans Javadoc classe. Pipeline de generation cohesif (classifyTasks → buildPreparationEntries → buildInjectionEntries → buildAssertionEntries → VerdictCalculator → buildExecutionSummary → buildEnvironmentInfo → buildVerdictReason). Ajouter ligne CC-02 dans la Javadoc de classe. Fichier : .../engine/DefaultReportEngine.java (classe, ligne 41-50).

[ISSUE-066] [2026-06-20] [CONFIRMED] [CRAFT-05] generate() 50 lignes (>40) — CC-02 ajoute dans Javadoc methode. Pipeline cohesif d'assemblage du CampaignReport (classification → construction entries → verdict → summary → environment → report final). Ajouter CC-02 dans la Javadoc de methode. Fichier : .../engine/DefaultReportEngine.java:91-96.

[ISSUE-066] [2026-06-20] [CONFIRMED] [CRAFT-05] buildExecutionSummary() 47 lignes (>40) — CC-02 ajoute dans Javadoc methode. Iteration cohesives prep→injection→assertion pour compteurs et durees agregees. Ajouter CC-02 dans la Javadoc de methode. Fichier : .../engine/DefaultReportEngine.java:239-241.

[ISSUE-068] [2026-06-20] [CONFIRMED] [TEST-04] Test shouldWrapConversionErrors() trompeur — remplace par 2 vrais tests d'erreur : shouldRethrowRenderExceptionAsIs (stub HtmlReportRenderer qui lance RenderException → rethrown as-is) et shouldWrapGenericExceptionInRenderException (stub HtmlReportRenderer qui lance RuntimeException → wrapped in RenderException). Stubs manuels (anonymous subclass) car Mockito incompatible Java 25 sur cette classe. 74 tests OK. Fichier : .../render/PdfReportRendererTest.java.

[ISSUE-069] [2026-06-20] [CONFIRMED] [CRAFT-07] executionId absent des logs dans les methodes privees writeRenderOutputs() (no_renderer_for_format, format_written) et copyGatlingDirectories() (gatling_dir_missing, gatling_copied, gatling_copy_interrupted, gatling_copy_failed). Passer executionId en parametre a ces methodes ou le stocker comme variable locale dans write() et l'inclure dans chaque log interne. Fichier : platform-reporting/.../output/ReportFileWriter.java.

[ISSUE-069] [2026-06-20] [CONFIRMED] [CRAFT-08] Magic strings sans constantes : "reports" (ligne 70, defaut outputDirectory), "campaign." (ligne 116, prefixe nom fichier), "gatling" (ligne 133, nom sous-repertoire Gatling). Extraire en private static final String DEFAULT_OUTPUT_DIRECTORY / REPORT_FILE_PREFIX / GATLING_SUBDIR. Fichier : platform-reporting/.../output/ReportFileWriter.java.

[ISSUE-070] [2026-06-20] [CONFIRMED] [CONFIG-01] Prefixe de configuration `platform.publishers` diverge de la spec qui montre `reporting.publishers`. Bien que coherent avec la convention `platform.*` pour les proprietes infrastructure (cf. PlatformDatasourcesProperties), l'utilisateur doit savoir que output settings utilisent `reporting.*` et publisher settings `platform.publishers.*`. Ajouter un commentaire Javadoc sur PublishersProperties expliquant la relation avec reporting.* et la raison du prefixe separe. Fichier : platform-infrastructure/.../publisher/PublishersProperties.java (classe).

[ISSUE-071] [2026-06-20] [CONFIRMED] [CRAFT-05] CC-02 Javadoc classe (pipeline cohesif Confluence: validation→buildConfluencePayload→buildStorageBody→HTTP POST→reponse/erreur, helpers escapeJson/escapeHtml/formatDuration/verdictColor indissociables). Fichier : .../confluence/ConfluenceReportPublisher.java (classe, lignes 43-51).

[ISSUE-071] [2026-06-20] [CONFIRMED] [CRAFT-05] CC-02 Javadoc publish() (pipeline cohesif: requireProperty→buildConfluencePayload→HTTP POST→gestion reponse/erreur). Fichier : .../confluence/ConfluenceReportPublisher.java (publish(), lignes 90-96).

[ISSUE-071] [2026-06-20] [CONFIRMED] [CRAFT-08] 4 constantes package-visible KEY_URL/KEY_SPACE_KEY/KEY_TOKEN/KEY_PARENT_PAGE_ID extraites et referencees dans le test via ConfluenceReportPublisher.KEY_*. Fichiers : ConfluenceReportPublisher.java (lignes 62-65) + ConfluenceReportPublisherTest.java.

[ISSUE-072] [2026-06-20] [CONFIRMED] [CRAFT-05] awsSign() 48 lignes (>40) sans CC-02 dans sa propre Javadoc. Le class-level CC-02 mentionne les helpers AWS mais le pattern etabli (ISSUE-062) exige CC-02 method-level pour chaque methode >40L. Ajouter bloc CC-02 dans la Javadoc de awsSign() expliquant le pipeline protocolaire AWS SigV4 cohesif (canonical request → string to sign → signing key → signature → authorization). Fichier : .../publisher/s3/S3ReportPublisher.java:474-521.

[ISSUE-072] [2026-06-20] [CONFIRMED] [SPEC-01] @ConditionalOnProperty(name = "reporting.publishers", value = "S3") absent (spec ISSUE-072). Code a @Component uniquement, coherent avec ISSUE-071. Ajouter commentaire Javadoc expliquant que l'activation est deleguee au MultiPublisherDispatcher. Fichier : .../publisher/s3/S3ReportPublisher.java:78.

[ISSUE-072] [2026-06-20] [CONFIRMED] [TEST-04] resolveCredentials() (lecture env vars AWS) non testee. Tests injectent AwsCredentials via constructeur, contournant cette methode. Ajouter shouldThrowWhenAwsEnvVarsNotSet verifiant IllegalStateException. Fichier : .../publisher/s3/S3ReportPublisherTest.java.

---

[ISSUE-073] [2026-06-20] [CONFIRMED] [PRECISION] Parametre `logDir` inutilise dans `runGit(Path logDir, Path workDir, String... command)`. Le parametre n'est jamais reference dans le corps de la methode. Supprimer `logDir` de la signature de `runGit()` et mettre a jour les 4 appelants (`gitClone`, `gitAdd`, `gitCommit`, `gitPush`). Fichier : platform-infrastructure/.../publisher/git/GitReportPublisher.java:239.

[ISSUE-078] [2026-06-20] [CONFIRMED] [TEST-04] Chemin prioritaire `System.getenv("RUNTIME_MODE")`/`MODE`/`TRANSPORT_TYPE` non couvert par les tests. Remplacer `System.getenv()` par `environment.getProperty()` dans `resolveMode()`/`resolveRole()`/`resolveTransportType()`. Ajouter 3 tests de priorite (env var override property) via `MockEnvironment.withProperty()`. Fichiers : platform-app/.../runtime/RuntimeModeResolver.java + RuntimeModeResolverTest.java.

---

[ISSUE-063] [2026-06-20] [CONFIRMED] [CRAFT-05] Classe 379 lignes (>300) sans marqueur CC-02 dans la Javadoc de classe. La methode evaluate() a son CC-02 (lignes 101-105) mais la classe elle-meme manque le marqueur. Ajouter bloc CC-02 dans la Javadoc de classe expliquant le pipeline cohesif d'evaluation WireMock (resolution parametres → validation metrique → resolution URL → HTTP /__admin/requests/count → extraction count → evaluation operateur → construction resultat). Pattern etabli : ISSUE-062, ISSUE-064, ISSUE-071. Fichier : platform-assertion/.../httpmock/HttpMockAssertionExecutor.java:32-46.

---

[ISSUE-079] [2026-06-20] [CONFIRMED] [CRAFT-02] ExecutionStatusResponse.phaseStatuses sans defensive copy dans le constructeur compact. Ajouter `phaseStatuses = phaseStatuses == null ? Map.of() : Map.copyOf(phaseStatuses);`. Fichier : platform-app/.../api/dto/ExecutionStatusResponse.java.

[ISSUE-079] [2026-06-20] [CONFIRMED] [CRAFT-08] Magic string "ACCEPTED" a la ligne 70 de ScenarioController. Extraire en constante `STATUS_ACCEPTED`. Fichier : platform-app/.../api/ScenarioController.java:70.

[ISSUE-079] [2026-06-20] [CONFIRMED] [CRAFT-07] Logs ApiExceptionHandler sans contexte (executionId absent). Pour handleExecution et handleReportGeneration, l'executionId pourrait etre extrait de l'exception si disponible. Fichier : platform-app/.../api/ApiExceptionHandler.java.

---

[ISSUE-080] [2026-06-20] [CONFIRMED] [CRAFT-01/DOC] PluginProperties Javadoc ligne 17 : "Si enabled est absent, la valeur par defaut est true" — mais le champ boolean primitif sans traitement dans le constructeur compact a pour defaut JVM false. Si l'intention est true, utiliser Boolean wrapper + null-check dans le constructeur compact. Sinon, corriger la Javadoc (true → false). Fichier : platform-app/.../plugin/PluginProperties.java:17.

[ISSUE-080] [2026-06-20] [CONFIRMED] [NAMING] Test shouldNotCrashWhenLoaderThrowsException — nom trompeur. Le test verifie que l'exception est PROPAGEE, pas qu'elle est absorbee. Le nom suggere l'inverse. Renommer en shouldPropagateLoaderException. Fichier : platform-app/.../plugin/PluginBootstrapTest.java:106.

[ISSUE-082] [2026-06-20] [CONFIRMED] [VERSION] Testcontainers 1.20.4 → 1.20.6 pour alignement inter-modules. platform-assertion, platform-infrastructure, platform-transport utilisent 1.20.6. Mettre a jour les 3 entrees <version> dans platform-app/pom.xml (testcontainers, postgresql, junit-jupiter). Fichier : platform-app/pom.xml:143,149,155.

[ISSUE-083] [2026-06-20] [CONFIRMED] [PRECISION] Exception `.dockerignore` inutile `!platform-app/target/performance-platform.jar` ligne 26. Le Dockerfile multi-stage compile depuis les sources (stage build) et copie le JAR via `COPY --from=build`, pas depuis le contexte de build hote. L'exception re-inclut un fichier qui n'est jamais lu depuis le contexte — code mort. Supprimer la ligne 26 et eventuellement la remplacer par un commentaire documentant le chemin du JAR de sortie. Fichier : platform-deployment/docker/.dockerignore:26.

[ISSUE-084] [2026-06-20] [CONFIRMED] [SPEC-01] `depends_on` des agents (agent-1, agent-2) ne reference pas `orchestrator`. La spec 09-deployment.md section 2 montre `depends_on: [kafka, orchestrator]` pour les agents. Ajouter `orchestrator` avec `condition: service_started` dans le bloc `depends_on` des deux agents. Fichier : platform-deployment/docker/docker-compose.yaml lignes 109-112, 130-133. → Verifie: agent-1 depends_on orchestrator service_started (ligne 113-114), agent-2 depends_on orchestrator service_started (ligne 138-139).

[ISSUE-084] [2026-06-20] [CONFIRMED] [SPEC-02] `AGENT_TAGS` absent des environnements agent-1 et agent-2. La spec 09-deployment.md section 2 montre `AGENT_TAGS: europe,standard` et `AGENT_TAGS: europe,high-memory`. ADR-008 confirme que les tags sont des metadonnees d'observabilite. Ajouter `AGENT_TAGS: "dev,standard"` pour agent-1 et `AGENT_TAGS: "dev,high-memory"` pour agent-2. Fichier : platform-deployment/docker/docker-compose.yaml lignes 99-106, 121-128. → Verifie: agent-1 AGENT_TAGS="dev,standard" (ligne 106), agent-2 AGENT_TAGS="dev,high-memory" (ligne 131).

[ISSUE-085] [2026-06-20] [CONFIRMED] [PRECISION] Terminologie "headless" incorrecte dans les commentaires des services `perf-kafka-service` et `perf-postgres-service` de service.yaml. Ces services sont des placeholders pour services externes (managed Kafka/PostgreSQL), pas des headless services K8s (qui requierent `clusterIP: None`). Remplacer "(headless placeholder)" par "(external service placeholder)" dans les deux commentaires d'en-tete (lignes 29 et 48). Fichier : platform-deployment/kubernetes/service.yaml.

## Historique

| Date | Issue | Recommandation | Statut |
|---|---|---|---|
| 2026-06-14 | ISSUE-027 | [CRAFT-07] Logging structuré SLF4J | CONFIRMED |
| 2026-06-14 | ISSUE-027 | [DOC] Javadoc @ConditionalOnProperty corrigé | CONFIRMED |
| 2026-06-14 | ISSUE-034 | [CRAFT-07] Logging structuré TransportAgentRegistration + HeartbeatScheduler | CONFIRMED |
| 2026-06-14 | ISSUE-034 | [CRAFT-08] Constante NO_EXECUTION | CONFIRMED |
| 2026-06-14 | ISSUE-034 | [TEST-06] CountDownLatch au lieu de Thread.sleep() | CONFIRMED |
| 2026-06-14 | ISSUE-035 | [CRAFT-07] Logging structure InMemoryAgentRegistry + AgentTtlMonitor | APPLIED |
| 2026-06-14 | ISSUE-035 | [TEST-06] Thread.sleep() → Awaitility InMemoryAgentRegistryTest | APPLIED |
| 2026-06-14 | ISSUE-035 | [SPEC] Annotations @Component/@ConditionalOnProperty manquantes | APPLIED |
| 2026-06-14 | ISSUE-035 | [SPEC-04] AgentTtlMonitor ne publie pas AgentLost | APPLIED |
| 2026-06-15 | ISSUE-036 | [CRAFT-05] Classe 684 lignes, 3 méthodes > 40 lignes | DEFERRED→ISSUE-037 |
| 2026-06-15 | ISSUE-036 | [CRAFT-08] Magic string "_partial_" → constante | CONFIRMED |
| 2026-06-15 | ISSUE-036 | [TEST-06] 2 Thread.sleep() résiduels | CONFIRMED |
| 2026-06-15 | ISSUE-036 | [IMPORT-01] Import inutilisé TaskStatus | CONFIRMED |
| 2026-06-15 | ISSUE-036 | [DESIGN] toExecutionContext() bridge à formaliser | DEFERRED→ISSUE-039 |
| 2026-06-15 | ISSUE-041 | [CRAFT-05] 6 méthodes >40 lignes — CC-02 justification + extraction pollMessages/sendMessages/sumPartitionOffsets | CONFIRMED |
| 2026-06-15 | ISSUE-041 | [CRAFT-07] executionId ajouté à tous les logs (threadé via ExecutionContext) | CONFIRMED |
| 2026-06-15 | ISSUE-041 | [PRECISION] Javadoc executeConsume() + pollMessages() expliquant max.poll.records et toTake | CONFIRMED |
| 2026-06-15 | ISSUE-041 | [CRAFT-08] Constantes OUTPUT_MESSAGES_CONSUMED/OUTPUT_LAG/OUTPUT_MESSAGES_PRODUCED/OUTPUT_MESSAGES_FAILED | CONFIRMED |
| 2026-06-16 | ISSUE-042 | [CRAFT-05] CC-02 justification ajoutee en Javadoc | APPLIED |
| 2026-06-16 | ISSUE-042 | [CRAFT-08] Constantes PARAM_DEPLOYMENT/ACTION/PORT/MAPPINGS_PATH/EXTERNAL_URL | APPLIED |
| 2026-06-16 | ISSUE-042 | [CRAFT-08] Constante DEFAULT_EXECUTION_KEY | APPLIED |
| 2026-06-16 | ISSUE-042 | [CRAFT-07] executionId ajoute aux logs mode EXTERNAL + handler erreur execute() | APPLIED |
| 2026-06-16 | ISSUE-043 | [CRAFT-05] CC-02 justification Javadoc + extraction executeProcess/execute | CONFIRMED |
| 2026-06-16 | ISSUE-043 | [TEST-06] 2 Thread.sleep(500) → Awaitility avec temp files | CONFIRMED |
| 2026-06-19 | ISSUE-045 | [PRECISION-01] pathsByExecution non alimenté — extraction executionKey + tracking dans executeCreate()/executeUpload() | CONFIRMED |
| 2026-06-19 | ISSUE-045 | [CRAFT-07] executionId absent des logs — ajouter executionKey dans tous les log.info/log.error | CONFIRMED |
| 2026-06-19 | ISSUE-047 | [CRAFT-05] CC-02 justification Javadoc constructeur DefaultPluginRegistry | CONFIRMED |
| 2026-06-19 | ISSUE-049 | [PRECISION-02] Release 23 override inutile pom.xml | CONFIRMED |
| 2026-06-19 | ISSUE-029 | [CRAFT-08] Magic string "@type" repete 5 fois dans KafkaMessageCodec — extraire constante TYPE_FIELD | APPLIED |
| 2026-06-19 | ISSUE-029 | [TEST-04] Ajouter tests null-checks parametres publics KafkaExecutionTransport (shouldThrowOnNull*) | APPLIED |
| 2026-06-20 | ISSUE-068 | [TEST-04] Test shouldWrapConversionErrors → 2 tests erreur avec stubs anonymes (Mockito incompatible Java 25) | APPLIED |

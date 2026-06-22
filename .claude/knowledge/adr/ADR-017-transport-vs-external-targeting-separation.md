# ADR-017 — Séparation Transport Interne vs Ressources Externes (Kafka & HTTP)

**Date** : 2026-06-21
**Statut** : ACCEPTED
**Décideurs** : Architect
**PDRs concernés** : PDR-020, PDR-021, PDR-022
**S'appuie sur** : ADR-002 (transport), ADR-009 (consumer group), ADR-013 (Spring-first), ADR-015

## Contexte

La plateforme utilise Kafka et HTTP pour **deux concerns radicalement différents**,
et le risque de confusion pour le Developer est élevé (les deux PDRs Kafka coexistent,
les deux PDRs HTTP coexistent) :

1. **Transport interne** (orchestrateur ↔ agents) — `platform-transport` :
   `KafkaExecutionTransport`, `HttpExecutionTransport`. Acheminent
   `TaskExecutionRequest`, `ExecutionEvent`, `AgentSignal`, `AgentLifecycleEvent`
   entre les composants de la plateforme. Configurés sous `transport.*`, sélectionnés
   par `@ConditionalOnProperty(name="transport.type", ...)` (ADR-002).
   Le broadcast per-agent (groupId = agentId) est régi par ADR-009.

2. **Ciblage de ressources externes (le SUT)** — `platform-infrastructure` :
   `KafkaProducerTaskExecutor`/`KafkaConsumerTaskExecutor` (via `KafkaClusterRegistry`,
   PDR-020) et `HttpClientTaskExecutor`/`MockServerTaskExecutor` (via `HttpTargetRegistry`,
   PDR-022). Produisent/consomment des messages et appellent des API **du système testé**.
   Configurés sous `platform.kafka-clusters.*` / `platform.http-targets.*` (ADR-015).

PDR-021 migre le transport interne vers Spring Kafka (`KafkaTemplate`,
`KafkaMessageListenerContainer`) **en même temps** que PDR-020 introduit du Spring
Kafka pour le ciblage externe. Les deux vivent dans le classpath, partagent les types
Spring Kafka (`ProducerFactory`, `KafkaTemplate`, `ConsumerFactory`) et risquent une
**collision de beans** et une **confusion conceptuelle**. PDR-021 anticipe en
qualifiant ses beans `transport*` (`transportKafkaTemplate`, `transportProducerFactory`,
`transportContainerFactory`). Cette séparation, critique, n'est documentée nulle part
de façon normative.

## Décision

**Nous décidons de séparer formellement et durablement le "transport interne" du
"ciblage de ressources externes", avec des namespaces, des modules, des beans et des
types Java distincts. Les deux ne doivent JAMAIS partager de configuration ni de bean.**

| Axe | Transport interne | Ciblage ressources externes (SUT) |
|---|---|---|
| Concern | Communication orchestrateur ↔ agents | Produire charge / asserter sur le SUT |
| Module | `platform-transport` | `platform-infrastructure` |
| Namespace YAML | `transport.*` (`transport.kafka.*`) | `platform.kafka-clusters.*`, `platform.http-targets.*` |
| Sélection | `@ConditionalOnProperty(transport.type)` (ADR-002) | Registry inconditionnel (ADR-015) |
| Beans Kafka | `transportKafkaTemplate`, `transportProducerFactory`, `transportContainerFactory` | `ProducerFactory`/`ConsumerFactory` éphémères créés par `KafkaClusterRegistry` |
| Sérialisation | `<String, byte[]>` (codec interne) | `<String, String>` (StringSerializer) |
| Payload | `TaskExecutionRequest`, `ExecutionEvent`, `AgentSignal` | messages métier du SUT (JSON arbitraire) |
| Règle de broadcast | groupId = agentId (ADR-009) | groupId = config cluster (`perf-consumer`) |

Règles normatives :

1. **Beans transport qualifiés `transport*`** — tout bean Spring Kafka/HTTP du module
   `platform-transport` porte le préfixe `transport` dans son nom de bean ET est
   conditionné par `@ConditionalOnProperty(name="transport.type", havingValue="KAFKA")`
   (resp. `HTTP`). Cela garantit qu'aucun bean transport n'existe quand le transport
   n'est pas actif (mode LOCAL), donc aucune collision avec les registries externes.

2. **Pas de bean partagé** — les executors de ciblage (`KafkaProducerTaskExecutor`…)
   NE consomment JAMAIS `transportKafkaTemplate` ni aucun bean `transport*`. Ils
   passent exclusivement par `KafkaClusterRegistry` / `HttpTargetRegistry`. Inversement,
   le transport interne n'utilise JAMAIS les registries externes.

3. **`platform.*` ≠ `transport.*`** — un Developer ne mélange jamais les deux
   namespaces. `transport.kafka.bootstrap-servers` configure la communication interne ;
   `platform.kafka-clusters.<id>.bootstrap-servers` configure une cible Kafka du SUT.

4. **Validité même cluster physique** — rien n'interdit que `transport.kafka` et un
   `platform.kafka-clusters.<id>` pointent vers le même broker physique ; ils restent
   logiquement et configurationnellement distincts.

## Conséquences

**Positives :**
- Élimine le risque de collision de beans (le déclencheur principal du PDR-021 :
  beans qualifiés `transport*`).
- Modèle mental clair : « est-ce de la plomberie interne ou est-ce que je tape sur le
  SUT ? » → le namespace répond.
- Les deux peuvent évoluer indépendamment (changer le transport en RabbitMQ n'affecte
  pas le ciblage Kafka du SUT, et inversement).
- Permet le mode LOCAL (aucun transport actif) tout en gardant le ciblage Kafka/HTTP
  du SUT pleinement fonctionnel.

**Négatives / Contraintes :**
- Deux configurations Kafka distinctes coexistent dans `application-*.yaml` — peut
  surprendre au premier abord. À documenter explicitement (commentaire YAML + README).
- Duplication apparente de la dépendance `spring-kafka` (transport + infrastructure) —
  acceptée : ce sont deux modules avec deux usages.
- Le Developer doit connaître cette distinction avant de toucher au Kafka/HTTP — d'où
  cet ADR comme référence obligatoire (à lier dans CLAUDE.md table de routing).

## Règles pour le Developer

- AVANT de toucher à du code Kafka ou HTTP, identifier le concern : transport interne
  (`platform-transport`, `transport.*`) ou ciblage SUT (`platform-infrastructure`,
  `platform.*`). Ne jamais mélanger.
- Tout bean Spring Kafka/HTTP de `platform-transport` : nom préfixé `transport`,
  conditionné `@ConditionalOnProperty(transport.type)`. Sinon → escalade.
- Les executors `Kafka*TaskExecutor` / `Http*TaskExecutor` injectent UNIQUEMENT
  `KafkaClusterRegistry` / `HttpTargetRegistry`. Jamais un bean `transport*`.
- Ne jamais lire `transport.kafka.*` depuis un executor de ciblage, ni
  `platform.kafka-clusters.*` depuis le transport.
- Conserver `<String,byte[]>` côté transport (codec) et `<String,String>` côté ciblage.

## Alternatives Considérées

| Option | Raison du rejet |
|---|---|
| Beans Kafka partagés entre transport et ciblage | Collision de beans, couplage des deux concerns, impossible de changer l'un sans l'autre. C'est précisément ce que PDR-021 évite avec les beans `transport*`. |
| Un seul namespace `kafka.*` global | Confond communication interne et cibles du SUT ; rend illisible quel broker fait quoi ; casse la possibilité d'avoir N clusters SUT + 1 transport. |
| Mettre le ciblage Kafka dans `platform-transport` | Mélange deux responsabilités dans un module ; viole la séparation hexagonale (le ciblage SUT est un adapter d'infrastructure, pas du transport). |
| Ne rien documenter (laisser les conventions de nommage parler) | Risque de confusion Developer jugé élevé par les PDRs eux-mêmes ; sans ADR, le Reviewer n'a pas de base pour refuser un mélange. |

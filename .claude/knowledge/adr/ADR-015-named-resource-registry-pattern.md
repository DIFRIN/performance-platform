# ADR-015 — Named Resource Registry Pattern (Standard pour toutes les ressources externes)

**Date** : 2026-06-21
**Statut** : ACCEPTED
**Décideurs** : Architect
**PDRs concernés** : PDR-020 (KafkaClusterRegistry), PDR-022 (HttpTargetRegistry)
**Généralise** : ADR-014 (DatasourceProvider)

## Contexte

ADR-014 a tranché la configuration des datasources : référence logique dans le
scénario, configuration technique (URL, credentials, pool) dans `application-*.yaml`
sous `platform.datasources.<id>`, binding via `@ConfigurationProperties` + `Map`,
résolution par un registre (`DatasourceProvider`) instancié par un `@Bean`.

PDR-020 et PDR-022 répliquent ce pattern, mot pour mot, pour deux nouvelles familles
de ressources externes :

- **Kafka** : `platform.kafka-clusters.<id>` → `KafkaClusterRegistry`
- **HTTP** : `platform.http-targets.<id>` → `HttpTargetRegistry`

La question : ADR-014 (spécifique aux datasources) suffit-il, ou faut-il un ADR qui
**officialise ce pattern comme standard obligatoire** pour toute ressource externe
future (gRPC, S3, Redis, SMTP…) ?

Sans ADR transverse, chaque nouvelle ressource risque de réinventer sa propre
convention de configuration (clés YAML incohérentes, résolution inline vs registre,
credentials parfois dans le scénario). ADR-014 reste lu comme « décision datasource »
et n'a pas l'autorité normative pour les autres ressources.

## Décision

**Nous décidons d'établir le "Named Resource Registry Pattern" comme standard
architectural obligatoire pour TOUTE ressource externe de la plateforme (datasource,
cluster Kafka, cible HTTP, et toute ressource future).**

Toute ressource externe DOIT respecter les 5 invariants suivants :

1. **Référence logique dans le scénario** — le `scenario.yaml` ne contient JAMAIS
   d'URL, d'adresse, de credential ou de configuration technique. Il référence la
   ressource par un **identifiant logique** (`datasource: customer-db`,
   `cluster: iot-sut`, `target: device-api`).

2. **Configuration technique dans `application-*.yaml`** — sous le namespace
   `platform.<resource-family>.<id>.*`. Les familles canoniques sont :
   `platform.datasources`, `platform.kafka-clusters`, `platform.http-targets`.
   Une nouvelle famille suit la même forme : `platform.<famille-au-pluriel>`.

3. **Credentials par variable d'environnement uniquement** — via la convention
   `${ENV_VAR:default}` (ADR-006), jamais en clair dans un fichier versionné Git
   contenant les valeurs réelles (CNF-03).

4. **Binding via `@ConfigurationProperties(prefix = "platform")` + `Map<String, …Properties>`** —
   les Properties sont des **records immuables** dont le constructeur compact
   applique les défauts et fige les collections (`Map.copyOf(...)`). Le nom de la
   ressource n'est jamais connu à la compilation : la `Map` dynamique est la seule
   forme acceptée (cf. ADR-014, sous-option C1 ; C2 rejetée).

5. **Résolution par un Registry instancié via `@Bean` de configuration** — le
   Registry n'est PAS un `@Component` scanné ; il est construit par une classe
   `@Configuration` + `@EnableConfigurationProperties`. Il expose au minimum
   `get(id)` (retourne `null` si inconnu) et offre un point d'extension pour
   l'enregistrement programmatique (plugins externes).

> Cette règle ne s'applique JAMAIS à `platform-domain` ni `platform-plugin-api`
> (0 dépendance Spring, ADR-004 / ADR-013).

ADR-014 devient une **instance** de ce standard (datasources). Il n'est pas
déprécié — il reste la référence détaillée du cas datasource.

### Distinction normative : ressources externes (SUT) vs transport interne

Le standard de cet ADR couvre **les ressources externes ciblées par les scénarios**
(le Système Sous Test). Il NE s'applique PAS au **transport interne**
orchestrateur↔agents, qui garde son propre namespace `transport.*` et sa propre
sélection par `@ConditionalOnProperty` (ADR-002). Voir ADR-017 pour la séparation.

## Conséquences

**Positives :**
- Un seul mental model pour toute la configuration de ressources externes — un
  Developer qui connaît les datasources sait configurer Kafka et HTTP sans relecture.
- Ajout d'une ressource = pure config YAML, zéro code (esprit CF-04).
- Credentials hors scénario Git garanti par règle, pas par discipline (CNF-03).
- Le même `scenario.yaml` tourne sur dev/staging/prod sans modification — seul
  `application-<env>.yaml` change.
- Base normative pour refuser en review toute config inline d'une nouvelle ressource.

**Négatives / Contraintes :**
- Le scénario n'est pas auto-suffisant : il exige un `application-*.yaml` cohérent.
  Une référence logique vers une entrée absente échoue à l'exécution (fail-fast).
- Légère cérémonie pour chaque famille (Properties record + Configuration + Registry).
  Acceptée : c'est le coût de la cohérence et de la testabilité.
- Trois familles aujourd'hui, mais les `@ConfigurationProperties` partagent le préfixe
  racine `platform` — veiller à ne pas créer de collision de sous-clés entre familles.

## Règles pour le Developer

> Application directe sur PDR-020, PDR-022, et toute ressource externe future.

- Pour toute nouvelle ressource externe, copier la structure ADR-014 :
  `Platform<Family>Properties` (record, `@ConfigurationProperties(prefix="platform")`),
  `<Family>Configuration` (`@Configuration` + `@EnableConfigurationProperties`),
  `<Family>Registry` (POJO instancié par `@Bean`, jamais `@Component`).
- Le record Properties applique TOUS ses défauts et fige ses `Map`/collections dans
  le constructeur compact (`Map.copyOf`). Aucune mutation post-construction.
- INTERDIT : URL, host, port, topic réel, path réel, credential dans un `scenario.yaml`.
  Si tu en vois un, escalade — c'est une violation de ce standard.
- Le Registry retourne `null` sur `get(id)` inconnu ; l'appelant DOIT fail-fast avec
  un message explicite (`"unknown cluster: <id>"`).
- Le namespace YAML est `platform.<famille-au-pluriel>` (kebab-case pour la famille :
  `kafka-clusters`, `http-targets`). Ne pas inventer une autre racine.
- Ne JAMAIS appliquer ce pattern à `transport.*` — c'est un concern séparé (ADR-017).

## Alternatives Considérées

| Option | Raison du rejet |
|---|---|
| Garder ADR-014 seul (datasource only) | N'a pas l'autorité normative transverse. Chaque ressource future réinventerait sa convention → incohérence et risque de credentials inline. |
| Un ADR par ressource (Kafka, HTTP, …) sans standard commun | Duplication des mêmes décisions, dérive inévitable entre familles. Le pattern est identique : un seul ADR transverse le capture. |
| Auto-injection `Map<String, DataSource>` Spring (C2 d'ADR-014) | Spring Boot n'auto-configure pas cette Map ; impose des bean names à tirets figés à la compilation, pas d'ajout par simple config. Déjà rejeté en ADR-014. |
| Configuration inline dans le scénario | Viole CNF-03 (credentials Git) et empêche la portabilité multi-environnement du scénario. |

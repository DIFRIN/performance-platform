# ADR-018 — Isolation des Services SUT d'Exemple (platform-examples hors build plateforme)

**Date** : 2026-06-21
**Statut** : ACCEPTED
**Décideurs** : Architect
**PDRs concernés** : PDR-023 (services SUT), PDR-024 (docker-compose-sut + scénarios)

## Contexte

PDR-023 introduit deux microservices (`iot-dispatcher`, `device-api`) servant de
**Système Sous Test (SUT)** pour démontrer la plateforme. Décisions structurantes
proposées par le System Designer :

- Ils vivent dans `platform-examples/`, **hors du `pom.xml` multi-module** de la
  plateforme.
- Ils sont en **Spring Boot 3.4.x**, alors que la plateforme impose **Spring Boot 4.x
  / Java 25** (CLAUDE.md §4, stack NON NÉGOCIABLE).
- Ils ont leur propre `docker-compose-sut.yaml` (PDR-024), lancé séparément, avec sa
  propre infrastructure (kafka-sut:9093, postgres-sut:5433, wiremock:8090).
- Ils sont volontairement simples (pas de couche service, pas de repo pattern,
  JdbcTemplate direct) — ce qui **viole délibérément** les règles architecturales de
  la plateforme (hexagonale, domaine sans framework, etc.).

Ces choix entrent en tension apparente avec des règles « NON NÉGOCIABLES ». Sans ADR,
un Reviewer ou un Developer pourrait légitimement signaler ces services comme des
violations (Spring Boot 3.4.x, pas d'archi hexagonale, JdbcTemplate dans un
`@RestController`). Il faut **statuer que ces règles ne s'appliquent PAS au SUT** et
poser la frontière.

## Décision

**Nous décidons que les services SUT d'exemple sont un artefact totalement isolé de la
plateforme : ils ne font PAS partie du produit, ne sont PAS soumis aux règles
architecturales de la plateforme, et vivent dans leur propre arborescence, build et
runtime.**

Règles normatives :

1. **Hors build Maven plateforme** — `platform-examples/` n'est PAS un module du
   `pom.xml` racine. `mvn clean install` à la racine NE construit JAMAIS le SUT.
   Chaque service SUT a son `pom.xml` autonome, buildé explicitement
   (`mvn -f platform-examples/<svc>/pom.xml package`).

2. **Stack indépendante** — le SUT peut utiliser une stack différente (Spring Boot
   3.4.x ici). La contrainte « Spring Boot 4.x / Java 25 » de CLAUDE.md §4 s'applique
   à la **plateforme uniquement**, PAS au SUT. Le SUT est un système tiers simulé.

3. **Règles architecturales NON applicables au SUT** — hexagonale, `platform-domain`
   sans Spring, communication par events, ExecutionContext immuable, registries
   nommés : AUCUNE de ces règles ne s'applique à `platform-examples/`. Le SUT est
   volontairement naïf (JdbcTemplate dans un controller, etc.) pour rester lisible et
   réaliste comme cible de test.

4. **Runtime isolé** — le SUT a son `docker-compose-sut.yaml` propre, sur des ports
   non conflictuels avec la plateforme (9093/5433/8082/8083/8090 vs 9092/5432/8080…).
   Il démarre et s'arrête indépendamment de la plateforme.

5. **Couplage uniquement par contrat externe** — la plateforme ne connaît le SUT que
   via ses endpoints/topics, configurés côté plateforme par registries nommés
   (`platform.kafka-clusters.iot-sut`, `platform.http-targets.device-api`, ADR-015).
   Aucune dépendance de code, aucun import croisé, dans les deux sens.

6. **Packages distincts** — `com.performance.examples.*` (et non
   `com.performance.platform.*`) pour rendre la frontière évidente et empêcher tout
   scan/dépendance accidentel.

## Conséquences

**Positives :**
- Le SUT démontre la plateforme sans la polluer : zéro impact sur le build, les tests,
  ou les contraintes du produit.
- Les règles « NON NÉGOCIABLES » restent intactes — elles ne sont pas affaiblies, elles
  ne s'appliquent simplement pas à un artefact qui n'est pas le produit.
- Le SUT peut évoluer (versions, stack) sans risque de régression sur la plateforme.
- Réalisme : un vrai SUT est rarement bien architecturé ; le simuler simplement est
  plus représentatif.

**Négatives / Contraintes :**
- Deux stacks Spring Boot dans le repo (4.x plateforme, 3.4.x SUT) — peut surprendre.
  Justifié et documenté ici ; ne PAS « harmoniser » le SUT vers 4.x.
- Le SUT n'est pas couvert par le CI build racine — sa compilation doit être vérifiée
  séparément (critères de Done PDR-023).
- Risque qu'un Developer applique par réflexe les règles plateforme au SUT — d'où cet
  ADR comme référence explicite de non-application.

## Règles pour le Developer

- NE PAS ajouter `platform-examples/` au `pom.xml` racine multi-module.
- NE PAS appliquer les règles architecturales plateforme (hexagonale, domaine pur,
  registries, ArchUnit) au code de `platform-examples/`. Ces services sont
  volontairement simples.
- NE PAS « upgrader » le SUT vers Spring Boot 4.x / Java 25 pour « cohérence » — c'est
  intentionnellement une stack tierce.
- Utiliser le package `com.performance.examples.*`, jamais `com.performance.platform.*`.
- AUCUN import croisé : le SUT n'importe rien de la plateforme, la plateforme n'importe
  rien du SUT. Le seul couplage est le contrat réseau (topics/endpoints) via les
  registries nommés (ADR-015).
- Le Reviewer NE doit PAS signaler les « violations » architecturales dans
  `platform-examples/` — elles sont autorisées par cet ADR.

## Alternatives Considérées

| Option | Raison du rejet |
|---|---|
| SUT comme modules du build plateforme | Pollue `mvn clean install`, impose la stack 4.x et les règles archi à du code qui doit rester simple, couple le cycle de vie produit↔démo. |
| SUT en Spring Boot 4.x (cohérence stack) | Aucun bénéfice : le SUT n'a pas besoin des features 4.x, et l'aligner suggère à tort qu'il fait partie du produit. La diversité de stack est plus réaliste. |
| Appliquer l'archi hexagonale au SUT | Sur-ingénierie d'un système jetable de démonstration ; nuit à la lisibilité (objectif explicite : SUT lisible, pas exemple d'archi). |
| Repo Git séparé pour le SUT | Sépare trop : les scénarios d'exemple (PDR-024) et le SUT évoluent ensemble ; les garder dans le même repo, mais hors build, est le bon compromis. |

# ADR-016 — Résolution des Noms Logiques (topics, paths) avec Fallback Transparent

**Date** : 2026-06-21
**Statut** : ACCEPTED
**Décideurs** : Architect
**PDRs concernés** : PDR-020 (resolveTopic), PDR-022 (resolvePath)
**Complète** : ADR-014, ADR-015

## Contexte

ADR-014 / ADR-015 résolvent l'identifiant **de la ressource** (`customer-db`,
`iot-sut`, `device-api`) vers sa configuration technique. Mais PDR-020 et PDR-022
introduisent un **second niveau de résolution**, à l'intérieur d'une ressource :

- **Kafka** : `KafkaClusterProperties.topics` — `Map<logical-topic, actual-topic>`.
  `resolveTopic("iot-sut", "iot-commands")` → `"iot-commands-prod-v3"`.
- **HTTP** : `HttpTargetProperties.paths` — `Map<logical-path, actual-path>`.
  `resolvePath("device-api", "submit-event")` → `"/api/v1/events"`.

Ce besoin n'existait pas pour les datasources (une datasource n'a pas de
sous-ressources nommées). C'est une **décision nouvelle**, non couverte par ADR-014 :
quel comportement quand le nom logique n'a PAS de mapping ? Quelle sémantique de
versioning ? Les deux PDRs spécifient indépendamment un « fallback = use name as-is »,
ce qui mérite d'être formalisé pour garantir un comportement uniforme et documenté
(notamment vis-à-vis de la sécurité : un nom logique non résolu pourrait laisser
fuiter un chemin réel dans le scénario).

## Décision

**Nous décidons que toute ressource exposant des sous-éléments nommés (topics, paths,
et équivalents futurs) fournit une résolution logique→réelle via une `Map` dans ses
Properties, avec un fallback transparent : si le nom logique n'a aucun mapping, il est
retourné tel quel.**

Règles normatives :

1. **Map de résolution dans les Properties** — `topics: Map<String,String>`,
   `paths: Map<String,String>`, figée par `Map.copyOf` (record immuable, ADR-015).

2. **Fallback "as-is"** — `resolve(resource, logicalName)` :
   - si `logicalName` ∈ map → retourne la valeur réelle ;
   - sinon → retourne `logicalName` inchangé.
   Le fallback est **silencieux au niveau INFO** mais DOIT émettre un log `DEBUG`
   (`action=name_resolution_fallback resource=… logical=…`) pour la traçabilité.

3. **Pas d'échec sur nom non mappé** — l'absence de mapping n'est PAS une erreur :
   elle autorise les chemins/topics directs ad hoc et la rétrocompatibilité. La
   validation réelle (topic existe, endpoint répond) est déléguée à l'exécution
   (broker Kafka, serveur HTTP), pas à la résolution.

4. **Le scénario utilise des noms logiques** — l'usage recommandé est le nom logique
   (`topic: iot-commands`, `path: submit-event`). Le fallback existe pour la
   souplesse, pas comme mode nominal. Recommandation portée par la doc/spec, pas
   imposée par le code (le code reste permissif).

## Conséquences

**Positives :**
- Versioning d'API et renommage de topics sans toucher au scénario : seule la map
  dans `application-<env>.yaml` change (`submit-event → /api/v2/events`).
- Rétrocompatibilité totale : les scénarios existants avec topics/paths réels inline
  continuent de fonctionner via le fallback.
- Comportement uniforme entre Kafka et HTTP (et ressources futures) — un seul contrat
  mental de résolution.
- Le scénario reste portable multi-environnement (objectif ADR-015) jusqu'au niveau
  sous-ressource.

**Négatives / Contraintes :**
- Le fallback masque les fautes de frappe : `topic: iot-comands` (typo) est traité
  comme un topic réel `iot-comands` au lieu d'échouer. Atténué par le log DEBUG et,
  côté Kafka, par `auto.create.topics` qui peut créer un topic fantôme (à surveiller).
- Le fallback peut laisser un chemin/topic réel apparaître dans le scénario (fuite
  d'info technique contraire à l'esprit ADR-015). Atténué par la recommandation
  d'usage logique — mais non bloqué par le code.

## Règles pour le Developer

- Implémenter `resolveTopic` / `resolvePath` exactement selon la règle 2 : lookup map,
  sinon retour `logicalName` tel quel. Aucune exception levée.
- Émettre un log `DEBUG` à chaque fallback (`action=name_resolution_fallback`).
- Ne JAMAIS faire échouer la résolution sur nom non mappé — l'échec appartient à la
  couche d'exécution (broker / serveur).
- Les maps `topics` / `paths` sont `Map.copyOf` dans le constructeur compact du record
  Properties (cohérent ADR-015).
- Dans les scénarios exemples (PDR-024), utiliser systématiquement des noms logiques,
  jamais des topics/paths réels — pour démontrer le bon usage.

## Alternatives Considérées

| Option | Raison du rejet |
|---|---|
| Échec strict si nom logique non mappé | Casse la rétrocompatibilité (scénarios avec paths/topics réels) et impose de déclarer chaque chemin ad hoc. Trop rigide pour les usages de préparation (ex: `/__admin/requests`). |
| Pas de résolution sous-ressource (topics/paths réels dans le scénario) | Empêche le versioning d'API et le renommage de topics sans modifier le scénario — contraire à l'objectif de portabilité d'ADR-015. |
| Résolution uniquement, sans fallback (map obligatoire et exhaustive) | Force à mapper même les chemins ponctuels (`/__admin/...`), verbeux et fragile. Le fallback couvre élégamment ces cas. |
| Laisser chaque PDR définir son propre fallback | Risque de divergence de sémantique entre Kafka et HTTP. Un ADR transverse garantit l'uniformité. |

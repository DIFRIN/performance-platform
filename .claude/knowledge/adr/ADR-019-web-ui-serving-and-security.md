# ADR-019 — Web IHM : Serving statique, sécurité, et frontière Modulith

**Date** : 2026-06-23
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : PDR-027/028/029 introduisent une IHM Web (HTML/CSS/vanilla JS) servie par
`platform-app`. La revue architecturale a relevé trois ambiguïtés/erreurs : (1) une propriété
de sécurité inexistante `security.jwt.enabled`, (2) la question de la frontière Spring Modulith
pour un module d'assemblage qui sert à la fois l'API REST, les ressources statiques et appelle
les use cases, (3) l'exposition des ressources statiques quand la sécurité est active.

---

## Contexte

Les PDRs supposaient un toggle de sécurité dédié à l'IHM (`security.jwt.enabled`) indépendant
du mode runtime. Or le code existant (`SecurityConfiguration`, ISSUE-081) pilote la sécurité
via `platform.security.enabled` (boolean) et active OAuth2/JWT uniquement si un issuer-uri est
configuré (`spring.security.oauth2.resourceserver.jwt.issuer-uri`). Les profils
`orchestrator`/`agent` positionnent `platform.security.enabled=true` ; le profil par défaut
(LOCAL) le laisse à `false`. **`security.jwt.enabled` n'existe nulle part dans le code.**

Par ailleurs `platform-app` est le module d'assemblage Spring Boot (la racine du graphe Modulith) :
il héberge déjà `ScenarioController`, `ApiExceptionHandler`, `SecurityConfiguration`,
`RuntimeModeResolver` et câble les use cases de `platform-application`. La question est : peut-il
aussi servir des ressources statiques et exposer de nouveaux controllers REST sans violer les
frontières Modulith ?

## Décision

**1. La sécurité de l'IHM réutilise le mécanisme existant, sans nouvelle propriété.**
Il n'y a PAS de `security.jwt.enabled`. La règle est :
- `platform.security.enabled=false` (défaut, LOCAL) → `permitAll()` sur tout, y compris `/`,
  `/assets/**`, `/api/v1/**`. C'est déjà le comportement de `SecurityConfiguration`.
- `platform.security.enabled=true` (orchestrator/agent) → la chaîne existante sécurise `/api/**`.
  Les ressources statiques de l'IHM (`/`, `/index.html`, `/assets/**`) DOIVENT être ajoutées
  aux matchers `permitAll()` de `SecurityConfiguration` (au même titre que `/actuator/health`),
  car ce sont des assets publics non sensibles (pas de login screen en v1) — les données
  réelles transitent par `/api/**` qui reste authentifié.

**2. `web.ui.enabled` est une property pure de serving, pas un toggle de sécurité.**
Elle contrôle uniquement le montage du `WebUiConfiguration` (resource handler). Conformément
à ADR-006, si un override conteneurisé est souhaité, l'opérateur utilise le binding Spring Boot
standard (`WEB_UI_ENABLED` est relaxé-bindé vers `web.ui.enabled` par Spring Boot — aucun
`EnvironmentPostProcessor` custom n'est requis car cette property n'est pas lue par un
`@ConditionalOnProperty` au bootstrap d'un autre module ; le relaxed binding natif suffit).

**3. `platform-app` reste le seul module autorisé à héberger l'adapter web entrant.**
Servir des ressources statiques + exposer des controllers REST + câbler les use cases dans
`platform-app` NE viole PAS Spring Modulith : `platform-app` est le module d'assemblage racine,
pas un module métier pair. Les controllers sont des adapters entrants qui appellent les **ports
in** de `platform-application` (use cases) — communication par appel de port, autorisée. Aucun
nouveau module Maven, aucun appel inter-module métier direct.

**4. En mode AGENT (DISTRIBUTED), aucun serveur web n'est démarré — surface HTTP entrante nulle.**
La contrainte d'accès est plus forte qu'« IHM désactivée ». La règle (clarification utilisateur,
2026-06-23) est :

| Mode runtime | API REST | IHM (navigateur) | CLI headless | Serveur web (Tomcat) |
|---|---|---|---|---|
| LOCAL (orchestrateur+agent, même JVM) | ✓ | ✓ | ✓ | ✓ |
| DISTRIBUTED / ORCHESTRATOR | ✓ | ✓ | ✓ | ✓ |
| DISTRIBUTED / AGENT | ✗ | ✗ | ✗ | ✗ |

En mode AGENT, la JVM démarre **sans aucun serveur HTTP entrant d'accès** :
`WebApplicationType.NONE` est forcé. L'agent est un pur worker — il se connecte en **sortant**
au transport (Kafka/HTTP/…), s'enregistre, et exécute des tâches. Il n'expose ni API, ni IHM,
ni mode CLI. `web.ui.enabled` est sans objet en mode AGENT (pas de contexte servlet à configurer) ;
désactiver l'IHM ne suffit pas — c'est l'absence totale de Tomcat qui est exigée.

> **Distinction importante** : l'adapter de **transport** HTTP entrant (`agent.http.callbackUrl`,
> spec 04 §8, actif uniquement quand `transport.type=HTTP`) n'est PAS une surface d'accès API/IHM/CLI.
> C'est le canal de réception de `TaskExecutionRequest` du transport, orthogonal à cette ADR.
> Cette ADR interdit le serveur web d'**accès** (port 8080, starter web servant API + IHM), pas le
> récepteur de transport. Quand le transport choisi n'est pas HTTP (ex. Kafka), l'agent n'a strictement
> aucune surface HTTP entrante.

Conséquence d'implémentation : la sélection du `WebApplicationType` doit être arrêtée dans `main()`
avant le bootstrap du contexte (même mécanisme que le CLI headless — ADR-021). En mode AGENT
(`MODE=AGENT` / `runtime.mode=DISTRIBUTED` côté agent), `main()` bootstrap avec
`WebApplicationType.NONE`. `WebUiConfiguration` n'est de toute façon jamais chargée (pas de contexte
servlet + garde `web.ui.enabled`), mais la garantie forte vient du `WebApplicationType.NONE` au
démarrage, pas d'une simple property.

## Justification

- Inventer `security.jwt.enabled` aurait créé deux mécanismes de sécurité concurrents et une
  property morte non câblée — exactement le type de divergence config/code que ADR-006 cherche
  à éviter.
- Le pattern "module d'assemblage = adapter entrant" est déjà établi (ScenarioController existe
  dans `platform-app` depuis PDR-018). L'IHM ne fait qu'étendre cet adapter.
- Exclure les assets statiques du filtre JWT est le pattern Spring Security standard : les assets
  sont publics, l'API porte l'authentification.

## Conséquences

**Positives** :
- Un seul mécanisme de sécurité, cohérent avec les profils existants.
- Pas de nouvelle property morte ; pas de nouveau module.
- Le relaxed binding Spring Boot couvre l'override env var sans code custom.

**Négatives / Contraintes** :
- En mode ORCHESTRATOR avec sécurité active, l'IHM statique est servie publiquement mais l'API
  qu'elle consomme exige un JWT → l'IHM v1 n'a pas de flux de login, donc l'IHM n'est réellement
  utilisable que lorsque `platform.security.enabled=false` OU derrière un reverse-proxy
  authentifiant. C'est acceptable en v1 (documenté), à traiter dans un PDR ultérieur (login/SPA auth).

**Fichiers impactés** :
- ISSUE-125 : remplacer toute référence à `security.jwt.enabled` par `platform.security.enabled` ;
  préciser que `SecurityConfiguration` (existant) est étendu pour `permitAll()` sur `/`, `/index.html`,
  `/assets/**` quand la sécurité est active. Préciser : mode AGENT → `WebApplicationType.NONE`
  (aucun Tomcat), pas seulement `web.ui.enabled=false`.
- PDR-028 : aligner la section "Sécurité" et "Règles de Comportement".
- `SecurityConfiguration.java` : ajouter les matchers statiques aux règles `permitAll()`.
- `specs/00-overview.md` : table canonique mode runtime × mode d'accès.
- `specs/04-agent-runtime.md` : note « AGENT = aucune surface d'accès HTTP entrante ».

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Nouvelle property `security.jwt.enabled` indépendante du mode | Crée un 2e mécanisme concurrent du `platform.security.enabled` existant ; property non câblée |
| Servir l'IHM depuis un nouveau module `platform-web` | Module Maven superflu ; `platform-app` est déjà l'adapter entrant racine |
| Sécuriser aussi `/assets/**` et `/` sous JWT | Sans login screen v1, rendrait l'IHM inaccessible ; assets non sensibles |

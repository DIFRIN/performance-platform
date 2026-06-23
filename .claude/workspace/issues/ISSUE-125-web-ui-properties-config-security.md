# ISSUE-125: WebUiProperties + config Spring conditionnelle + securite static/UI

**PDR** : PDR-028
**Module** : `platform-app`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-124
**Taille** : M
**Estime** : M

---

## Objectif

Mettre en place l'activation conditionnelle de l'IHM (`web.ui.enabled`), le static resource handler
(UI a `/`, assets sous `/assets/**`), les regles de securite, et la desactivation de l'UI sur le profil AGENT.

> **CORRECTION ARCHITECT (ADR-019)** : il n'existe AUCUNE propriete `security.jwt.enabled`.
> La securite est pilotee par `platform.security.enabled` (boolean, deja en place — SecurityConfiguration
> ISSUE-081). Defaut `false` (LOCAL) → permit-all. `true` (orchestrator/agent) → `/api/**` securise via
> OAuth2/JWT si `spring.security.oauth2.resourceserver.jwt.issuer-uri` est configure. Quand la securite est
> active, AJOUTER `/`, `/index.html`, `/assets/**` aux matchers `permitAll()` de `SecurityConfiguration`
> (assets publics, pas de login screen v1). NE PAS inventer de nouvelle propriete JWT.

---

## Fichiers à Créer / Modifier

```
platform-app/src/main/java/com/performance/platform/app/web/
  ├── WebUiProperties.java              — @ConfigurationProperties("web.ui") record { boolean enabled }
  └── WebUiConfiguration.java           — @ConditionalOnProperty(web.ui.enabled=true) WebMvcConfigurer

platform-app/src/main/java/com/performance/platform/app/config/
  └── SecurityConfiguration.java        — MODIF : permit-all static+UI+API si JWT off ; JWT si on

platform-app/src/main/resources/
  └── application.yaml                  — MODIF : web.ui.enabled (defaut false), profil agent override false

platform-app/src/test/java/com/performance/platform/app/web/
  ├── WebUiConfigurationTest.java       — UI servie si enabled, 404 sinon, jamais sur AGENT
  └── WebSecurityTest.java              — permit-all (JWT off) vs securise (JWT on)
```

---

## Interfaces à Implémenter

```java
@ConfigurationProperties(prefix = "web.ui")
public record WebUiProperties(boolean enabled) {}

@Configuration
@ConditionalOnProperty(prefix = "web.ui", name = "enabled", havingValue = "true")
public class WebUiConfiguration implements WebMvcConfigurer {
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) { /* static + "/" → index.html */ }
}
```

---

## Règles Spécifiques

- `web.ui.enabled` defaut `false`. Profil AGENT force `false` (UI jamais exposee).
  Override conteneurise : `WEB_UI_ENABLED=true` (relaxed binding Spring Boot natif, pas d'EnvironmentPostProcessor custom).
- **Mode AGENT = aucun serveur web (ADR-019)** : la garantie n'est PAS `web.ui.enabled=false`,
  c'est `WebApplicationType.NONE` forcé au démarrage. En mode AGENT (DISTRIBUTED, `MODE=AGENT`),
  la JVM ne démarre AUCUN Tomcat : ni API, ni IHM, ni CLI. L'agent ne fait que des connexions
  sortantes vers le transport (Kafka/HTTP) pour s'enregistrer et exécuter des tâches. La sélection
  du `WebApplicationType` se décide dans `main()` (même point que le CLI headless — ADR-021).
  `WebUiConfiguration` n'est de toute façon jamais chargée en AGENT, mais la garantie forte vient
  du `WebApplicationType.NONE`, pas d'une property. NB : l'éventuel récepteur de transport HTTP
  entrant de l'agent (`agent.http.callbackUrl`, spec 04 §8, seulement si `transport.type=HTTP`)
  n'est PAS une surface d'accès API/IHM/CLI — il est hors scope de cette garantie et de cette Issue.
- Static handler : `/` sert `index.html` ; `/assets/**` sert les assets ; routes SPA en hash gerees client-side.
- Securite (ADR-019) : pilotee par `platform.security.enabled` (deja existant — PAS de `security.jwt.enabled`).
  - `platform.security.enabled=false` (defaut LOCAL) → permit-all (deja le cas).
  - `platform.security.enabled=true` (orchestrator/agent) → `SecurityConfiguration` (existant) est ETENDU :
    ajouter `/`, `/index.html`, `/assets/**` aux matchers `permitAll()` (a cote de `/actuator/health/**`),
    `/api/**` reste `authenticated()` avec `oauth2ResourceServer().jwt()` si issuer-uri configure.
- `@EnableConfigurationProperties(WebUiProperties.class)` ajoute a l'app.
- **CORRECTION ARCHITECT** : le mode "headless run-and-exit sur `--scenario=`" N'EXISTE PAS dans le code
  (`PerformancePlatformApplication` fait un `SpringApplication.run` standard, toujours web). Le PDR-028
  le presente a tort comme "comportement existant PDR-018". CETTE ISSUE ne doit PAS pretendre preserver
  un chemin inexistant ni l'implementer (hors scope PDR-028). Retirer ce critere de Done. Si le headless
  run-and-exit est souhaite, il fera l'objet d'une Issue dediee (et probablement d'un ADR sur
  `WebApplicationType.NONE` conditionnel a la presence de `--scenario=`).

---

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur, 0 warning
- [ ] `web.ui.enabled=true` → `/` sert index.html ; `false` → 404 sur `/`
- [ ] Profil AGENT : UI jamais servie ET aucun serveur web (`WebApplicationType.NONE`, pas de Tomcat) — ADR-019
- [ ] `platform.security.enabled=false` (defaut) → permit-all ; `=true` → `/api/**` securise ET `/`+`/assets/**` permit-all (ADR-019)
- [ ] ~~`--scenario=` present → run-and-exit~~ RETIRE — comportement inexistant (voir correction Architect, ADR à venir si besoin)
- [ ] `.claude/workspace/progress.md` : ISSUE-125 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (WebUiProperties, WebUiConfiguration)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel

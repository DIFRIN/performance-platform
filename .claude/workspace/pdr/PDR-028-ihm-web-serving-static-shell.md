# PDR-028 — IHM Web Serving & Static Shell

**Module Maven** : `platform-app`
**Package** : `com.performance.platform.app.web` (config Spring), `src/main/resources/static` (assets)
**Statut** : WAITING
**Specs de reference** : Design valide utilisateur (Web IHM), ADR-013 (Spring-first infra)
**Depend de** : PDR-027 (IHM Backend API Extensions)
**Issues** : ISSUE-125, ISSUE-126

---

## Responsabilite

Ce PDR met en place le serving de l'IHM Web : activation conditionnelle, handler de ressources
statiques, regles de securite, et le shell HTML/CSS/JS de base (layout, navigation, routeur a hash).

L'IHM est 100% HTML/CSS + vanilla JS, sans aucune etape de build Node/npm. Les assets sont servis
directement par le static resource handler de Spring MVC, depuis le meme fat JAR.

**Ce que ce PDR fait** :
1. `WebUiProperties` (`web.ui.enabled`) + configuration Spring conditionnelle.
2. Static resource handler : UI servie a `/`, assets sous `/assets/**`, API sous `/api/v1/**`.
3. Securite : permit-all sur les routes statiques et l'UI quand JWT desactive ; JWT activable par property.
4. Profil AGENT : aucune UI exposee.
5. `index.html` (shell SPA) + CSS de layout + composant de navigation + squelette de routeur a hash.

**Ce que ce PDR fait sur les modes d'acces** (meme JVM) :
- `--scenario=x.yaml` present en argument CLI → run-and-exit headless, AUCUN serveur web demarre (ADR-021).
- Argument absent → sert API + IHM en mode long-running.
- IHM active sur LOCAL + ORCHESTRATOR uniquement. En mode AGENT, la JVM ne demarre AUCUN serveur web
  (`WebApplicationType.NONE`, ADR-019) : ni API, ni IHM, ni CLI. La garantie n'est pas "UI desactivee"
  mais "aucun Tomcat". Voir la matrice canonique dans `specs/00-overview.md`.

**Ce que ce PDR NE fait PAS** :
- N'implemente PAS les vues fonctionnelles (executions, detail, agents, upload, report) → PDR-029.
- N'introduit AUCUN build front (pas de Node, npm, bundler).
- N'ajoute PAS d'ecran de login (pas de login en v1).
- N'ajoute aucun nouveau module Maven.

---

## Interfaces Publiques

### WebUiProperties

```java
package com.performance.platform.app.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proprietes de l'IHM Web, prefixe "web.ui".
 *
 * @param enabled active le serving de l'IHM et des ressources statiques.
 *                Defaut false : l'IHM n'est servie que si explicitement activee.
 */
@ConfigurationProperties(prefix = "web.ui")
public record WebUiProperties(boolean enabled) {
}
```

### WebUiConfiguration

```java
package com.performance.platform.app.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration du serving de l'IHM Web.
 * <p>
 * Active uniquement quand {@code web.ui.enabled=true}. Enregistre le handler
 * de ressources statiques (UI a "/", assets sous "/assets/**") et redirige
 * les routes SPA inconnues vers index.html (hash routing cote client).
 * <p>
 * Jamais active sur le profil AGENT.
 */
@Configuration
@ConditionalOnProperty(prefix = "web.ui", name = "enabled", havingValue = "true")
public class WebUiConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Signatures detaillees dans ISSUE-125.
    }
}
```

### Securite (extension de la SecurityConfiguration existante)

```java
// Regle ajoutee a la chaine de securite existante (ISSUE-081) :
// - quand security.jwt.enabled est faux (defaut) : permit-all sur "/", "/assets/**", "/api/v1/**"
// - quand security.jwt.enabled est vrai : oauth2ResourceServer().jwt() applique aux "/api/v1/**"
// La propriete JWT est INDEPENDANTE du mode (pas mode-driven).
```

---

## Regles de Comportement

- **`web.ui.enabled`** : defaut `false`. L'IHM n'est servie que si `true`. Profils LOCAL et ORCHESTRATOR
  peuvent l'activer ; profil AGENT ne l'active jamais (et `WebUiConfiguration` n'est pas chargee si elle l'est par erreur, garde mode).
- **Detection headless** : si `--scenario=` est present dans les arguments CLI, l'application execute le scenario
  et sort (run-and-exit), SANS demarrer le serveur web ni l'IHM. C'est le comportement existant (PDR-018) ;
  l'IHM n'altere pas ce chemin.
- **Routing** : UI servie a `/` (sert `index.html`). Routes SPA en hash (`/#/executions`, `/#/executions/{id}`, etc.) —
  donc une seule entree serveur `/`, le routeur client gere le reste. Assets sous `/assets/**`. API sous `/api/v1/**` (PDR-027).
- **JWT** : activable via property `security.jwt.enabled=true` (ou equivalent existant). NON pilote par le mode.
  Defaut : permit-all sur LOCAL et ORCHESTRATOR. Pas de login screen en v1.
- **Port** : 8080 (defaut Spring Boot, deja en place).
- **Zero build front** : aucun `package.json`, aucun bundler. Les fichiers `.html`/`.css`/`.js` sont livres tels quels
  sous `src/main/resources/static/`.
- **Shell SPA** : `index.html` charge le CSS de layout, le composant nav, et le routeur. Le routeur a hash mappe
  les routes vers des fonctions de rendu (implementees en PDR-029). Squelette uniquement ici.

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-027 (IHM Backend API)        → endpoints /api/v1/** consommes par l'IHM
  PDR-018 (Application Assembly)   → SecurityConfiguration existante, profils Spring, RuntimeModeResolver
  Spring MVC                       → WebMvcConfigurer, ResourceHandlerRegistry

Ce PDR est utilise par :
  PDR-029 (IHM Frontend Views)     → les vues s'inserent dans le shell et le routeur
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues ISSUE-125 et ISSUE-126 sont DONE
- [ ] `WebUiProperties` et `WebUiConfiguration` dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] `mvn test -pl platform-app -q` passe
- [ ] `web.ui.enabled=true` → IHM servie a `/` ; `false` → 404 sur `/`
- [ ] Profil AGENT : IHM jamais servie ET aucun serveur web (`WebApplicationType.NONE`, pas de Tomcat) — ADR-019
- [ ] `--scenario=` present → aucun serveur web, run-and-exit preserve
- [ ] JWT desactive (defaut) → permit-all ; JWT active → securisation des `/api/v1/**`
- [ ] `index.html` + CSS layout + nav + routeur a hash livres sous `src/main/resources/static/`

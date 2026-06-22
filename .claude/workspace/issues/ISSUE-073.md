# ISSUE-073 — GitReportPublisher

**PDR** : PDR-016 (Report Publishers)
**Module** : `platform-infrastructure`
**Package** : `com.performance.platform.infrastructure.publisher.git`
**Taille** : M
**Bloquée par** : ISSUE-070 (MultiPublisherDispatcher)

---

## Objectif

Implémenter `GitReportPublisher` — publie les artefacts de rapport (JSON + HTML + logs Gatling)
dans un dépôt Git distant.

---

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/publisher/git/
  └── GitReportPublisher.java          (classe principale, ~300 lignes)

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/publisher/git/
  └── GitReportPublisherTest.java      (tests unitaires, ~15 tests)
```

---

## Interfaces Utilisées

- `com.performance.platform.reporting.ReportPublisher`
- `com.performance.platform.reporting.PublicationException`
- `com.performance.platform.reporting.PublicationTarget`
- `com.performance.platform.reporting.model.CampaignReport`
- `com.performance.platform.reporting.model.PublisherConfig`

---

## Spécification

### Constructeur

```java
@Component
public class GitReportPublisher implements ReportPublisher {

    // No-arg constructor for Spring
    public GitReportPublisher() { ... }

    // Package-visible for testing
    GitReportPublisher(ExecutorService executor) { ... }
}
```

### Configuration (via PublisherConfig.properties())

| Clé | Description | Requis | Défaut |
|-----|-------------|--------|--------|
| `repo-url` | URL du dépôt Git (HTTPS) | Oui | — |
| `branch` | Branche cible | Non | `main` |
| `username` | Username Git (HTTPS auth) | Non | — |
| `token` | Token d'accès (prioritaire sur username/password) | Non | — |
| `password` | Mot de passe Git (HTTPS auth) | Non | — |
| `path` | Chemin dans le dépôt | Non | Racine |
| `commit-message` | Message de commit | Non | `Report {reportId}` |

### Algorithme `publish()`

1. Valider `repo-url` présent → `PublicationException` si absent
2. Créer répertoire temporaire
3. Cloner le dépôt (shallow: `--depth 1 --branch <branch>`)
4. Construire `report.json` et `report.html` dans le sous-répertoire `path/`
5. `git add` les fichiers
6. `git commit -m "message"`
7. `git push`
8. Nettoyer le répertoire temporaire

### Authentification

- Si `token` fourni → `git clone https://{token}@github.com/...` (token dans l'URL)
- Sinon si `username` + `password` → `git clone https://{username}:{password}@...`
- Sinon → sans authentification (repos publics)
- Les secrets ne sont jamais loggés

### Gestion d'Erreurs

- Échec clone → `PublicationException`
- Échec commit/push → `PublicationException`
- Timeout configurable (30s par défaut)

---

## Critères de Done

- [ ] `GitReportPublisher.java` implémente `ReportPublisher`
- [ ] `getTarget()` retourne `PublicationTarget.GIT`
- [ ] `@Component` + Javadoc activation déléguée à MultiPublisherDispatcher
- [ ] CC-02 Javadoc classe (pipeline Git cohesif)
- [ ] CC-02 Javadoc `publish()` (> 40 lignes probable)
- [ ] Tests : ~15 tests couvrant cas nominal, erreurs, auth
- [ ] `mvn test -pl platform-infrastructure -Dtest="GitReportPublisherTest"` → BUILD SUCCESS
- [ ] 0 warning
- [ ] `platform-infrastructure` tests totaux OK

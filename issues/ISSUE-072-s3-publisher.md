# ISSUE-072 — S3ReportPublisher

**PDR** : PDR-016
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-070
**Estime** : M

---

## Objectif

Implémenter `S3ReportPublisher` : upload des fichiers du rapport vers un bucket S3.

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/publisher/s3/
  └── S3ReportPublisher.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/publisher/s3/
  └── S3ReportPublisherTest.java — client S3 mocké (ou Testcontainers LocalStack)
```

## Interfaces à Implémenter

```java
@Component
@ConditionalOnProperty(name = "reporting.publishers", value = "S3")
public class S3ReportPublisher implements ReportPublisher {
    public PublicationTarget getTarget() { return PublicationTarget.S3; }
    public void publish(CampaignReport report, PublisherConfig config) throws PublicationException { /* ... */ }
}
```

## Règles Spécifiques

- Config : `bucket`, `prefix`, `region`.
- Credentials via chaîne de credentials AWS standard (jamais en clair).
- Upload des fichiers HTML/PDF/JSON. I/O sous Virtual Threads.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Upload testé (mock ou LocalStack)
- [ ] `progress.md` mis à jour : ISSUE-072 → DONE
- [ ] `context/interfaces-registry.md` : `S3ReportPublisher` → STABLE

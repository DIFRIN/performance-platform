# ISSUE-132: Root pom : import du BOM spring-boot-dependencies + spring-modulith
**Status**: APPROVED
**PDR**: PDR-030
**Module**: UNKNOWN
**Started**: 2026-06-29T16:40+02:00
**IssueFile**: issues/ISSUE-132-root-pom-spring-boot-bom-import.md

> 📄 Full specification: `.claude/workspace/issues/ISSUE-132-root-pom-spring-boot-bom-import.md`

## Reviewer Feedback — 2026-06-29T20:15+02:00
Tests KO à investiguer : platform-infrastructure (49 échecs — WireMock/Jetty 11 absent) et platform-transport (1 flaky — contention port SocketExecutionTransportTest). Vérifier si le BOM spring-boot-dependencies 4.0.0 a modifié la version résolue de WireMock (3.12.1 → version gérée par le BOM). Le Developer doit s'assurer que le build reste vert après l'ajout des BOMs.

### Corrections appliquées
1. **WireMock/Jetty** : Ajout de 15 entrées `<dependencyManagement>` Jetty 11.0.24 AVANT le BOM spring-boot-dependencies pour empêcher l'override vers Jetty 12.1.4 (incompatible avec WireMock 3.12.1).
2. **JUnit version** : Déplacé `junit-bom` AVANT `spring-boot-dependencies` — le BOM Spring Boot 4.0.0 gérait JUnit Jupiter en 6.0.1, écrasant le 5.11.4 du junit-bom. Ordre inversé = `first wins` = JUnit reste en 5.11.4.
3. **Résultat** : `mvn clean install -q` → BUILD SUCCESS, tous les modules verts (~1150+ tests, 0 échec).

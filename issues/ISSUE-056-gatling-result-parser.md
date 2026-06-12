# ISSUE-056 — GatlingResultParser (stats.json → InjectionResult)

**PDR** : PDR-013
**Module** : `platform-injection-gatling`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-055
**Estime** : M

---

## Objectif

Implémenter `GatlingResultParser` qui lit le `stats.json` Gatling et produit un `InjectionResult`.

## Fichiers à Créer

```
platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/result/
  ├── GatlingResultParser.java
  ├── DefaultGatlingResultParser.java
  └── ResultParsingException.java

platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/result/
  └── DefaultGatlingResultParserTest.java — stats.json de référence → InjectionResult
```

## Interfaces à Implémenter

```java
public interface GatlingResultParser {
    InjectionResult parse(Path gatlingResultDirectory, TaskId taskId) throws ResultParsingException;
}
public class ResultParsingException extends RuntimeException {
    public ResultParsingException(String message, Throwable cause) { super(message, cause); }
}
```

## Règles Spécifiques

- Lire `stats.json` pour : totalRequests, successful/failed, errorRate (%), throughput, p50/p75/p90/p95/p99, max/min/mean.
- `gatlingReportDirectory` = répertoire HTML Gatling.
- `errorRate` en pourcentage (0.0–100.0).
- Fichier manquant/malformé → `ResultParsingException`.
- `rawStats` = map brute pour assertions custom.

## Critères de Done

- [ ] `mvn test -pl platform-injection-gatling -q` → 0 erreur
- [ ] Un `stats.json` de référence parse en `InjectionResult` correct
- [ ] `progress.md` mis à jour : ISSUE-056 → DONE
- [ ] `context/interfaces-registry.md` : `GatlingResultParser` → STABLE

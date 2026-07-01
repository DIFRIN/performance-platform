# PDR-034 — AssertionSummary Domain Records

**Module Maven** : `platform-domain`
**Package** : `com.performance.platform.domain.assertion`
**Status** : WAITING
**Specs de reference** : `.claude/knowledge/specs/07-assertion-framework.md`, `.claude/workspace/assertion-distributed-analysis.md`
**Depend de** : PDR-001 (deja DONE)
**Issues** : ISSUE-148, ISSUE-149

---

## Responsabilite

Definit les deux nouveaux records du domaine qui constituent le format de sortie unifie de toutes les assertions (interne et externe). Ces records sont places dans `TaskResult.outputs` sous la cle `"assertion"` pour la serialisation via `ExecutionEvent`. Remplace l'ancien `Evidence` comme format de transport principal tout en conservant `AssertionResult` et `Evidence` pour l'usage interne des executors.

Ce PDR ne touche PAS aux interfaces de plugin (`platform-plugin-api`) ni aux implementations d'assertion.

---

## Interfaces Publiques

```java
package com.performance.platform.domain.assertion;

import com.performance.platform.domain.id.TaskId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured result of an assertion evaluation, designed for report generation
 * and serialization over ExecutionTransport.
 * <p>
 * All assertion executors produce an AssertionSummary in their TaskResult.outputs
 * under the key "assertion". This is the ONLY format that report engines and
 * transport codecs need to understand for assertions.
 * <p>
 * Record immuable -- 0 annotation framework. Copies defensives sur collectedData et history.
 */
public record AssertionSummary(
    TaskId assertionId,
    AssertionStatus verdict,          // PASSED | FAILED | SKIPPED | ERROR
    String description,                // human-readable justification for the report
    Map<String, Object> collectedData, // metrics/samples/data used in the decision
    List<AssertionSample> history,     // interval-based sampling history (empty list = point-in-time)
    Duration evaluationDuration,
    Instant evaluatedAt
) {
    public AssertionSummary {
        Objects.requireNonNull(assertionId, "assertionId required");
        Objects.requireNonNull(verdict, "verdict required");
        Objects.requireNonNull(description, "description required");
        Objects.requireNonNull(evaluationDuration, "evaluationDuration required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt required");
        collectedData = collectedData == null ? Map.of() : Map.copyOf(collectedData);
        history = history == null ? List.of() : List.copyOf(history);
    }

    /**
     * Verdict: true iff verdict is PASSED.
     */
    public boolean isPassed() {
        return verdict == AssertionStatus.PASSED;
    }

    /**
     * Factory for point-in-time assertions (empty history).
     */
    public static AssertionSummary of(
            TaskId assertionId,
            AssertionStatus verdict,
            String description,
            Map<String, Object> collectedData,
            Duration evaluationDuration,
            Instant evaluatedAt
    ) {
        return new AssertionSummary(assertionId, verdict, description,
                collectedData, List.of(), evaluationDuration, evaluatedAt);
    }
}
```

```java
package com.performance.platform.domain.assertion;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A single sample taken during interval-based assertion monitoring.
 * Each sample records the observed value at a point in time.
 * <p>
 * Record immuable -- 0 annotation framework.
 */
public record AssertionSample(
    Instant sampledAt,
    double observedValue,
    String unit,
    Map<String, Object> metadata   // extra context (e.g., topic partition offset, endpoint)
) {
    public AssertionSample {
        Objects.requireNonNull(sampledAt, "sampledAt required");
        Objects.requireNonNull(unit, "unit required");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
```

---

## Regles de Comportement

- `AssertionSummary` est le format de SERIALISATION publique. Les executors d'assertion continuent d'utiliser `AssertionResult` et `Evidence` en interne.
- `collectedData` contient les metriques brutes utilisees pour la decision (ex: `metric=p95`, `actualValue=420.0`, `expectedValue=500.0`, `operator=LT`, `unit=ms`).
- `history` est vide pour les assertions ponctuelles (point-in-time). Il est non-vide uniquement pour les assertions basees sur des intervalles (assertions avec `linkedTo`).
- `description` est un texte lisible par un humain, destine au rapport. Format conseille : `"VERDICT: metric actualValue operator expectedValue"` (ex: `"PASSED: p95 420.00 ms < 500.00 ms"`).
- Les parametres `stopBehavior` et `gracePeriodDuration` sont des parametres YAML uniquement. Ils ne sont PAS des champs d'`AssertionSummary`. Ils sont lus par l'engine depuis `StepDefinition.parameters()` au moment du dispatch et transmis via `ExecutionLifecycleSignal`. Leur effet est visible dans `history` (nombre d'echantillons) et `evaluationDuration`.
- Les deux records sont serialisables (Jackson) sans annotations -- `Map<String, Object>` et `List<...>` sont des types standards.

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-001 (platform-domain records)  -> TaskId, AssertionStatus (deja STABLE)

Ce PDR est utilise par :
  PDR-035 (plugin-api evolution)     -> AssertionResultMapper reference AssertionSummary
  PDR-036 (assertion registration)   -> AssertionResultMapper produit AssertionSummary
  PDR-037 (engine unified dispatch)  -> lit AssertionSummary depuis TaskResult.outputs
```

---

## Critères de Done (PDR complet)

- [ ] `AssertionSummary` record compile dans `platform-domain` (0 erreur)
- [ ] `AssertionSample` record compile dans `platform-domain` (0 erreur)
- [ ] Pas d'annotation Spring/JPA/Jackson dans les records (ArchUnit passe)
- [ ] Les records sont dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] Les tests unitaires verifient les copies defensives et le constructeur compact

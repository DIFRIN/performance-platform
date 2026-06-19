# ISSUE-054 — LoadModelTranslator (8 LoadModelTypes → OpenInjectionStep)

**PDR** : PDR-013
**Module** : `platform-injection-gatling`
**Statut** : IN REVIEW
**Priorité** : P1
**Bloquée par** : ISSUE-006, ISSUE-011
**Estime** : L

---

## Objectif

Créer le module `platform-injection-gatling` et implémenter `LoadModelTranslator` qui traduit
les 8 `LoadModelType` en `List<OpenInjectionStep>` (Gatling Java DSL).

## Fichiers à Créer

```
platform-injection-gatling/pom.xml — dépend de domain, plugin-api, gatling-http, gatling-app
platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/load/
  ├── LoadModelTranslator.java
  └── DefaultLoadModelTranslator.java

platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/load/
  └── DefaultLoadModelTranslatorTest.java — les 8 types
```

## Interfaces à Implémenter

```java
public interface LoadModelTranslator { List<OpenInjectionStep> translate(LoadModel model); }

@Component
public class DefaultLoadModelTranslator implements LoadModelTranslator { /* switch sur LoadModelType */ }
```

## Règles Spécifiques (mapping)

- RAMP → `rampUsersPerSec(from).to(to).during(d)` par stage
- CONSTANT → `constantUsersPerSec(n).during(d)`
- SPIKE → ramp base→spike sur spikeDuration
- STAIR → escalier (`incrementUsersPerSec` ou stairs custom)
- SOAK → constant + ramp initial
- BURST → `nothingFor()` + `atOnceUsers()` répété
- RAMP_UP_DOWN → rampUp + hold + rampDown
- CUSTOM → interpolation linéaire entre les points

## Critères de Done

- [ ] `mvn test -pl platform-injection-gatling -q` → 0 erreur
- [ ] Chaque `LoadModelType` produit la bonne séquence d'`OpenInjectionStep`
- [ ] `.claude/progress.md` mis à jour : ISSUE-054 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `LoadModelTranslator` → STABLE

# ISSUE-240 — Mix pondéré HTTP

**PDR** : PDR-061
**Module** : `platform-injection-gatling`
**Statut** : APPROVED
**Priorité** : P2
**Bloquée par** : ISSUE-208
**Estime** : M (1-3h)

---

## Objectif

La simulation HTTP générée tire une requête par utilisateur selon les poids.

> Référence : ADR-070.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/http/
  ├── RequestMix.java, WeightedRequest.java — (création)
  └── HttpScenarioSimulation.java   — sélection pondérée (modification)
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/http/RequestMixTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-061` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Sélection par générateur aléatoire à graine (hash du plan + index du share), pas par `randomSwitch` non déterministe.
- Chaque requête Gatling porte le `name` déclaré (utilisé par le parser).
- Forme mono-requête = `RequestMix.single` nommé `default` ; comportement inchangé.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Sur 10 000 tirages, répartition conforme aux poids à ±2 %
- [ ] Même graine => même séquence
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-240 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

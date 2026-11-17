# ISSUE-241 — Mix pondéré Kafka

**PDR** : PDR-061
**Module** : `platform-injection-gatling`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : ISSUE-240
**Estime** : M (1-3h)

---

## Objectif

Même mécanisme pour la simulation Kafka générée : gabarits de messages pondérés.

> Référence : ADR-070.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/kafka/ — simulation et spécification de message (modification)
(+ tests)
```

## Interfaces à Implémenter

> Signatures dans `PDR-061` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Réutiliser `RequestMix` (généraliser le type du `spec` si nécessaire, sans dupliquer la sélection).
- Publication asynchrone conservée (`KafkaPublishAction`).

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Répartition conforme aux poids
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-241 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

# ISSUE-104 — Delete DevicePopulationTaskExecutor + DeviceCheckTaskExecutor from platform-infrastructure

**PDR** : PDR-025
**Module** : `platform-infrastructure`
**Statut** : DONE
**Priorite** : P0 (bloquant — code example-only ne doit pas etre dans le module de production)
**Bloquee par** : aucune
**Estime** : M (1-3h)

---

## Objectif

Supprimer les `DevicePopulationTaskExecutor` et `DeviceCheckTaskExecutor` du module `platform-infrastructure`. Ces classes sont du code de demonstration incorrectement place dans le module de production. La simulation de devices appartient exclusivement a `distribution/examples/sut/` ou elle sert de code SUT de demo. Leur presence dans `platform-infrastructure` fait qu'ils sont scannes, enregistres comme Spring beans, et disponibles dans l'execution de production — ce qui est une fuite architecturale.

## Fichiers a Supprimer

```
infra/platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/device/
  ├── DevicePopulationTaskExecutor.java     — DELETE
  └── DeviceCheckTaskExecutor.java          — DELETE
```

Si le repertoire `infra/platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/device/` devient vide apres suppression, le supprimer egalement.

## Fichiers a Verifier et Mettre a Jour

```
infra/platform-infrastructure/src/main/java/com/performance/platform/infrastructure/
  └── (tout fichier de configuration Spring qui pourrait @ComponentScan ou @Bean ces classes)

infra/platform-infrastructure/src/test/java/
  └── (tout test ArchUnit qui attend ces classes dans le package .executor.device)
  └── (tout test unitaire testant directement ces classes)
```

## Regles Specifiques

- **Ne pas supprimer** le repertoire `distribution/examples/sut/` — les SUT (iot-dispatcher, device-api) restent
- **Ne pas supprimer** `DeviceRepository` dans les SUT (iot-dispatcher, device-api) — ce sont des classes internes aux SUT, pas des TaskExecutors
- **Ne pas supprimer** les stubs WireMock (`distribution/docker/wiremock/` ou `distribution/examples/wiremock/`) — ils peuvent etre utiles pour d'autres scenarios
- Si des tests dans `platform-infrastructure` ou `platform-scenario-dsl` referencent le package `.executor.device`, les mettre a jour
- Verifier les tests ArchUnit (ex: `InfrastructurePackageSeparationTest`) qui pourraient enumerer les sous-packages de `.infrastructure.executor`
- Verifier que la suppression ne casse pas `device-check-perf.yaml` — ce scenario sera supprime separement via ISSUE-109

## Criteres de Done

- [ ] `DevicePopulationTaskExecutor.java` supprime
- [ ] `DeviceCheckTaskExecutor.java` supprime
- [ ] `mvn compile -pl :platform-infrastructure -q` -> 0 erreur
- [ ] `mvn test -pl :platform-infrastructure -q` -> tous les tests passent
- [ ] Aucun import de `com.performance.platform.infrastructure.executor.device` dans le code source
- [ ] Les tests ArchUnit passent (pas d'attente de classes dans `.executor.device`)
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-104 -> DONE

# ISSUE-103 — Remove standalone WireMock from docker-compose-sut.yaml

**PDR** : PDR-025
**Module** : `platform-deployment`
**Statut** : APPROVED
**Priorite** : P0 (bloquant — WireMock standalone cree une confusion architecturale)
**Bloquee par** : aucune
**Estime** : S (< 1h)

---

## Objectif

Supprimer le service WireMock standalone du fichier `docker-compose-sut.yaml`. Dans l'architecture correcte, WireMock est demarre EMBEDDED par un agent via `MockServerTaskExecutor`, pas en tant que service infrastructure separe. Le SUT (`iot-dispatcher`) ne doit plus dependre d'un service `wiremock` standalone.

## Fichiers a Modifier

```
platform-deployment/examples/
  └── docker-compose-sut.yaml      — Remove wiremock service block + its health check dependency
```

## Changements specifiques

1. **Supprimer le service `wiremock`** entierement (tout le bloc de service)
2. **Supprimer la dependance `wiremock`** dans `iot-dispatcher: depends_on`
3. **Supprimer le volume mount wiremock** (s'il restait un `./wiremock` dans volumes:)
4. **Mettre a jour le commentaire d'en-tete** du fichier pour refleter 4 services au lieu de 5
5. **Supprimer le port 8090** de la liste des ports exposes dans les commentaires
6. **Supprimer la variable `IOT_DEVICE_GATEWAY_URL`** de l'environnement `iot-dispatcher`

## Regles Specifiques

- Ne pas toucher aux autres services (postgres-sut, kafka-sut, iot-dispatcher, device-api)
- Le network `sut-net` reste (utilise par les 4 services restants)
- Le volume `sut_postgres_data` reste
- Verifier que le repertoire `wiremock/` avec ses mappings n'est plus reference comme volume mount

## Criteres de Done

- [ ] `docker compose -f platform-deployment/examples/docker-compose-sut.yaml config` -> YAML valide, 4 services, 0 wiremock
- [ ] Aucune reference a `wiremock` dans le fichier
- [ ] Aucune reference a `wiremock` dans `iot-dispatcher` (ni depends_on, ni variable d'env)
- [ ] `mvn test -pl platform-deployment -q` -> 0 erreur
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-103 -> DONE

# ISSUE-110 — Clean device entries from interfaces-registry

**PDR** : PDR-025
**Module** : `.claude/workspace` (fichiers de tracking IA uniquement)
**Statut** : APPROVED
**Priorite** : P2 (normal — maintenance du registre, non bloquant)
**Bloquee par** : ISSUE-104 et ISSUE-109 (device code + scenario supprimes, le registre doit refleter l'etat reel)
**Estime** : S (< 1h)

---

## Objectif

Nettoyer le fichier `interfaces-registry.md` de toute reference aux classes et scenarios device qui n'existent plus. Apres ISSUE-104 (suppression des DevicePopulationTaskExecutor et DeviceCheckTaskExecutor) et ISSUE-109 (suppression de device-check-perf.yaml), le registre contient des entrees zombies qui doivent etre marquees comme REMOVED ou supprimees.

## Fichier a Modifier

```
.claude/workspace/
  └── interfaces-registry.md
```

## Changements specifiques

### 1. platform-infrastructure — `.executor` section

Rechercher et marquer comme supprimees (ou supprimer les lignes) les entrees suivantes si elles existent :
- `DevicePopulationTaskExecutor`
- `DeviceCheckTaskExecutor`
- Toute entree dans le package `.executor.device`

Format a utiliser :
```
| `DevicePopulationTaskExecutor` | ❌ REMOVED | PDR-025 | ISSUE-104 (example-only code deleted from production module) |
| `DeviceCheckTaskExecutor` | ❌ REMOVED | PDR-025 | ISSUE-104 (example-only code deleted from production module) |
```

### 2. platform-deployment section

Si une entree pour `device-check-perf.yaml` existe, la marquer comme REMOVED :
```
| `scenarios/device-check-perf.yaml` | ❌ REMOVED | PDR-025 | ISSUE-109 (depends on deleted executors) |
```

### 3. platform-deployment (PDR-025) section

Mettre a jour les entrees existantes de PDR-025 pour refleter les nouveaux IDs d'Issue (ISSUE-103 a ISSUE-110 au lieu de ISSUE-103 a ISSUE-108). Les nouvelles entrees :
```
| `docker-compose-sut.yaml` (wiremock removed) | ⬜ PLANNED | PDR-025 | ISSUE-103 |
| `DevicePopulationTaskExecutor` + `DeviceCheckTaskExecutor` DELETED | ❌ REMOVED | PDR-025 | ISSUE-104 |
| `scenarios/http-api-mock-agent-local.yaml` | ⬜ PLANNED | PDR-025 | ISSUE-105 |
| `scenarios/http-api-mock-agent-distributed.yaml` | ⬜ PLANNED | PDR-025 | ISSUE-106 |
| `docker-compose-wiremock-agent.yaml` | ⬜ PLANNED | PDR-025 | ISSUE-107 |
| `README.md` Mock-as-Agent architecture | ⬜ PLANNED | PDR-025 | ISSUE-108 |
| `scenarios/device-check-perf.yaml` DELETED | ❌ REMOVED | PDR-025 | ISSUE-109 |
| Device entries cleanup in registry | — | PDR-025 | ISSUE-110 |
```

## Regles Specifiques

- Utiliser le statut `❌ REMOVED` (pas de suppression pure de lignes — le registre garde l'historique)
- Referencer le PDR et l'Issue qui a cause la suppression
- Ne pas toucher aux entrees des autres PDRs
- Si des entrees device sont dans d'autres sections du registre, les traiter de la meme maniere

## Criteres de Done

- [ ] Toutes les entrees `DevicePopulationTaskExecutor` et `DeviceCheckTaskExecutor` sont `❌ REMOVED`
- [ ] L'entree `device-check-perf.yaml` est `❌ REMOVED`
- [ ] La section PDR-025 du registre reflete les 8 nouvelles Issues (ISSUE-103 a ISSUE-110)
- [ ] Aucune entree zombie restante
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-110 -> DONE

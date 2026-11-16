# ISSUE-226 — API, IHM et CLI : verdict INVALID

**PDR** : PDR-059
**Module** : `platform-bootstrap`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-224
**Estime** : M (1-3h)

---

## Objectif

Exposer `INVALID` dans les DTO REST, le badge IHM et le code de sortie CLI.

> Référence : ADR-069.

## Fichiers à Créer / Modifier

```
bootstrap/src/main/java/com/performance/platform/app/cli/CliScenarioRunner.java — INVALID => exit 2 (modification)
bootstrap/src/main/resources/static/assets/js/views/ (executions.js, execution-detail.js, report.js) — badge (modification)
bootstrap/src/main/resources/static/assets/css/app.css — style du badge (modification)
bootstrap/src/test/java/com/performance/platform/app/cli/ — test du code de sortie
```

## Interfaces à Implémenter

> Signatures dans `PDR-059` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Code de sortie 2 pour INVALID (ADR-069 §5).
- Badge distinct de FAILED et WARNING, avec infobulle explicative.

## Critères de Done

- [ ] `mvn test -pl :platform-bootstrap -q` → 0 erreur
- [ ] CLI retourne 2 sur un run INVALID
- [ ] Badge visible dans la liste et le détail
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-226 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées

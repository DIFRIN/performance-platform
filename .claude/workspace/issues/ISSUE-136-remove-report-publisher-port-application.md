# ISSUE-136 — Supprimer ReportPublisherPort de platform-application

**PDR** : PDR-031
**Module** : `platform-application`
**Statut** : APPROVED
**Priorité** : P1 (critique)
**Bloquée par** : ISSUE-135
**Estime** : S (< 1h)

---

## Objectif

Supprimer le port sortant `ReportPublisherPort` (aucun appelant réel — uniquement implémenté
par `MultiPublisherDispatcher`, supprimé en ISSUE-135) et nettoyer le test de compilation des
ports. Conforme à ADR-022 (YAGNI : pas de port no-op « au cas où »).

## Fichiers à Créer / Modifier

```
SUPPRIMER :
platform-application/src/main/java/com/performance/platform/application/ports/out/ReportPublisherPort.java

MODIFIER :
platform-application/src/test/java/.../PortsCompileTest.java (ou équivalent)
  — retirer toute référence à ReportPublisherPort
```

## Interfaces à Implémenter

> Suppression d'interface publique actée par ADR-022 :

```java
// SUPPRIMER entièrement
public interface ReportPublisherPort { /* ... */ }
```

## Règles Spécifiques

- Avant suppression, `grep -rn "ReportPublisherPort" platform-application platform-execution-engine
  platform-app` → doit ne renvoyer que la définition + le test (aucun appelant métier). Si un
  appelant inattendu existe, **escalader Architect** (ADR-022 affirme l'absence d'appelant).
- Nettoyer `PortsCompileTest` : retirer l'assertion/import sur `ReportPublisherPort`.
- Ne pas toucher les autres ports out (`ExecutionRepository`, `AgentRegistry`, etc.).

## Critères de Done

- [ ] `mvn test -pl platform-application -q` → 0 erreur
- [ ] `ReportPublisherPort.java` n'existe plus
- [ ] `grep -rn "ReportPublisherPort" .` → vide (hors ADR/PDR/Issue docs)
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` : `ReportPublisherPort` → `❌ REMOVED (ADR-022)`
</content>

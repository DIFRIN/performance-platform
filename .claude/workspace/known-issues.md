# Known Issues & Technical Debt

> Liste des problèmes connus, des workarounds temporaires et de la dette technique acceptée.
> Mis à jour par tout agent qui identifie un problème mais ne peut pas le corriger
> dans le périmètre de sa tâche courante.
>
> Objectif : ne pas perdre d'information entre sessions. Tout problème identifié
> mais non résolu doit être ici, pas dans un TODO dans le code.

---

## Format

```markdown
### [KI-XXX] Titre court
**Sévérité** : CRITICAL | HIGH | MEDIUM | LOW
**Identifié par** : [Agent] le [date]
**Phase impactée** : Phase X
**Symptôme** : [Ce qui se passe de mauvais]
**Cause** : [Pourquoi — si connue]
**Workaround** : [Ce qui est fait en attendant — ou "aucun"]
**Résolution prévue** : Phase X — [description courte]
**Fichiers concernés** : [liste]
```

---

## Issues Ouvertes

### [KI-001] toPayload(AgentDescriptor) — mapping manuel sans synchronisation automatique
**Sévérité** : MEDIUM
**Identifié par** : Architect le 2026-06-14
**Phase impactée** : Phase 7 (PDR-009)
**Symptôme** : Si `AgentDescriptor` gagne un nouveau champ, le payload de `ExecutionEvent`/`AgentLifecycleEvent` ne l'inclura pas automatiquement — la serialisation sera silencieusement incomplète.
**Cause** : Mapping manuel champ par champ dans `TransportAgentRegistration.toPayload()`. Pas de Jackson dans ce module à ce stade.
**Workaround** : Commentaire `// NOTE: synchroniser avec AgentDescriptor si le record évolue` ajouté sur la méthode.
**Résolution prévue** : ISSUE-028 (Transport properties + Configuration) — introduction de Jackson dans le module transport.
**Fichiers concernés** : `platform-agent-runtime/.../registration/TransportAgentRegistration.java`

---

## Issues Résolues

_Aucune pour l'instant. Les issues résolues sont déplacées ici avec leur date de résolution._

---

## Dette Technique Acceptée

> Décisions conscientes de ne pas faire "la chose parfaite" pour avancer.
> Chaque entrée doit avoir une justification et un plan de remboursement.

| ID | Description | Justification | Remboursement prévu |
|---|---|---|---|
| DT-001 | _(exemple) Heartbeat in-memory, pas persisté_ | _(exemple) Complexité Phase 7 déjà élevée_ | _(exemple) Phase 8 si besoin validé_ |

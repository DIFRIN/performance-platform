# ADR-012 — AgentLifecycleEvent : Séparation des événements de cycle de vie d'agent

**Date** : 2026-06-14
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : Review architecturale post-implémentation ISSUE-034 (TransportAgentRegistration) et ISSUE-035 (AgentRegistry). Le sentinel `NO_EXECUTION` identifié comme design smell lors de la revue.

---

## Contexte

`TransportAgentRegistration` publie les événements de cycle de vie d'agent (enregistrement,
heartbeat, désenregistrement) via `ExecutionTransport.publishEvent(ExecutionEvent)`. Problème :
`ExecutionEvent` possède un champ `executionId` obligatoire, or ces événements ne sont PAS
liés à une exécution de scénario. La solution actuelle utilise un sentinel :

```java
static final ExecutionId NO_EXECUTION = ExecutionId.of("NO_EXECUTION");
```

Ce sentinel est un design smell pour deux raisons :

1. **Risque de filtrage silencieux** : tout handler de l'orchestrateur qui filtre les events
   par `executionId` peut accidentellement traiter ou ignorer les events de lifecycle d'agent
   en comparant à `NO_EXECUTION` au lieu d'une vraie `ExecutionId`.
2. **Sémantique incorrecte** : un événement `AgentRegistered` n'a pas d'`ExecutionId`.
   Forcer ce champ pollue le modèle et nécessite de documenter une exception non intuitive.

Trois options ont été évaluées :
- **Option A** : Conserver `NO_EXECUTION` — simple, mais sémantique incorrecte
- **Option B** : Créer `AgentLifecycleEvent` séparé — propre, nécessite extension de l'interface
- **Option C** : Rendre `executionId` nullable dans `ExecutionEvent` — modifie une interface
  publique critique ⚡, rompt tous les handlers existants

---

## Décision

**Nous créons `AgentLifecycleEvent` comme type distinct dans `platform-transport`, et nous
étendons `ExecutionTransport` avec deux nouvelles méthodes pour publier et souscrire ces
événements.**

```java
// platform-transport — nouveau record
public record AgentLifecycleEvent(
    EventId id,
    AgentId agentId,
    String eventType,
    Map<String, Object> payload,
    Instant occurredAt
) {
    public static final String AGENT_REGISTERED   = "AgentRegistered";
    public static final String AGENT_HEARTBEAT    = "AgentHeartbeat";
    public static final String AGENT_DEREGISTERED = "AgentDeregistered";
    // compact constructor avec validation + Map.copyOf(payload)
}

// platform-transport — nouveau handler fonctionnel
@FunctionalInterface
public interface AgentLifecycleEventHandler {
    void onEvent(AgentLifecycleEvent event);
}

// ExecutionTransport — deux nouvelles méthodes ⚡
public interface ExecutionTransport {
    // ... méthodes existantes ...

    /**
     * Publie un événement de cycle de vie d'agent (registration, heartbeat,
     * désenregistrement). Ces événements n'ont pas d'ExecutionId.
     */
    void publishAgentEvent(AgentLifecycleEvent event) throws TransportException;

    /**
     * Souscrit aux événements de cycle de vie d'agent.
     * Utilisé côté orchestrateur pour alimenter l'AgentRegistry.
     */
    Subscription subscribeAgentEvents(AgentLifecycleEventHandler handler);
}
```

`TransportAgentRegistration` utilisera `publishAgentEvent()` à la place de `publishEvent()`.
L'orchestrateur (ISSUE-036) utilisera `subscribeAgentEvents()` pour alimenter `AgentRegistry`.

---

## Justification

**Pourquoi Option B et non Option A (sentinel) :**
- Élimine le risque de filtrage silencieux côté orchestrateur — les events de lifecycle
  arrivent sur un canal séparé, impossible de les confondre avec des events d'exécution.
- Le modèle de transport reflète la sémantique réelle : lifecycle ≠ execution.
- Prépare correctement l'implémentation Kafka (ADR-009) : les events d'agent peuvent
  être sur un topic dédié (`agent-lifecycle`) séparé du topic d'exécution.

**Pourquoi Option B et non Option C (executionId nullable) :**
- Option C modifie le contrat de `ExecutionEvent` et force tous les handlers existants
  à gérer le null — coût de migration élevé pour un gain équivalent.
- Option B est additive : les handlers existants ne sont pas touchés.

**Pourquoi ajouter à `ExecutionTransport` et non créer `AgentLifecycleTransport` séparé :**
- Évite la fragmentation de l'abstraction de transport : un seul point d'entrée pour
  toute communication orchestrateur ↔ agents.
- Les implémentations concrètes (Kafka, RabbitMQ, InMemory) ont déjà la complexité
  d'implémenter `ExecutionTransport` — ajouter deux méthodes est moins coûteux
  que de gérer deux interfaces et deux beans Spring conditionnels.
- Cohérence avec `Subscription` qui est déjà partagé entre les deux types d'events.

---

## Conséquences

**Positives** :
- Suppression du sentinel `NO_EXECUTION` — le code est sémantiquement correct
- Séparation claire des canaux execution / lifecycle dans le transport
- Prépare naturellement le topic Kafka dédié pour les events d'agent (ADR-009)
- `AgentLifecycleEvent` est plus petit qu'`ExecutionEvent` (pas de `correlationId`,
  pas d'`executionId`) — payload réseau réduit pour les heartbeats fréquents

**Négatives / Contraintes** :
- `ExecutionTransport` est une interface publique critique ⚡ — toutes les
  implémentations existantes et futures doivent implémenter les deux nouvelles méthodes
- `InMemoryExecutionTransport` doit être mis à jour (ISSUE-034 ou ISSUE-036)
- `TransportAgentRegistration` doit migrer de `publishEvent` vers `publishAgentEvent`

**Fichiers impactés** :
- `platform-transport` : création `AgentLifecycleEvent.java`, `AgentLifecycleEventHandler.java`
- `platform-transport` : modification `ExecutionTransport.java` (+ 2 méthodes)
- `platform-transport` : modification `InMemoryExecutionTransport.java` (implémentation)
- `platform-agent-runtime` : modification `TransportAgentRegistration.java` (migration)
- `platform-agent-runtime` : modification `TransportAgentRegistrationTest.java` (migration tests)
- `.claude/context/interfaces-registry.md` : ajout `AgentLifecycleEvent`, `AgentLifecycleEventHandler`

**Impact sur ISSUE-036 (DistributedAgentRuntime)** :
- L'orchestrateur utilisera `transport.subscribeAgentEvents(handler)` pour alimenter
  `AgentRegistry` au lieu de filtrer des `ExecutionEvent` par type.

## Périmètre d'implémentation

Cette décision est implémentée dans le cadre d'**ISSUE-034** (refactoring) avant ISSUE-036.
Le Developer doit appliquer les changements avant de démarrer ISSUE-036.

---

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Option A — `NO_EXECUTION` sentinel | Sémantique incorrecte, risque de filtrage silencieux |
| Option C — `executionId` nullable dans `ExecutionEvent` | Modifie le contrat de `ExecutionEvent`, force gestion null sur tous les handlers existants |
| Interface `AgentLifecycleTransport` séparée | Fragmente l'abstraction, double les beans Spring conditionnels, complexité inutile |

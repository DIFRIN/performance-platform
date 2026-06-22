# ADR-008 — Suppression de AgentAllocator : Filtre de Spécialisation Côté Agent

**Date** : 2026-06-08
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : Introduction des agents spécialisés — chaque agent déclare les tasks
qu'il peut exécuter. Décision : où vit la logique de sélection ?

---

## Contexte

L'architecture précédente utilisait `AgentAllocator` côté orchestrateur pour sélectionner
un agent cible avant le dispatch. Avec la spécialisation, l'orchestrateur devrait connaître
les capacités de chaque agent et choisir. La nouvelle spec demande que l'orchestrateur
ne réalise aucune sélection métier.

## Décision

**La responsabilité de déterminer si une task doit être exécutée appartient à l'agent.**

L'orchestrateur publie une `TaskExecutionRequest` en broadcast sans targetAgentId.
Chaque agent filtre via `TaskSpecializationFilter` basé sur `agent.supportedTasks`.
`AgentAllocator` est supprimé. `AgentSelector` (critères de tags) est supprimé.

## Justification

- **Couplage réduit** : l'orchestrateur ne dépend pas du registre de capacités pour dispatcher.
- **Extensibilité** : ajouter un agent spécialisé ne nécessite pas de modifier l'orchestrateur.
- **Symétrie des transports** : le modèle broadcast est naturel pour Kafka (consumer groups)
  et RabbitMQ (FANOUT exchange). Pour HTTP, l'orchestrateur envoie à tous les agents
  enregistrés supportant la task — même logique, sémantique identique.
- **Multi-agent natif** : plusieurs agents avec la même spécialisation coexistent sans
  configuration spéciale — chacun claim et exécute indépendamment.

## Conséquences

- `AgentAllocator`, `AgentSelector` supprimés du domaine.
- `AgentAvailabilityChecker` remplace `AgentAllocator` (vérification de présence seulement).
- La spécialisation est statique (déclarée à la config, immuable après démarrage).
- Un agent "généraliste" déclare explicitement toutes les tasks qu'il supporte.
- `AgentDescriptor.tags` conservé comme métadonnée (observabilité) mais ignoré pour le routing.

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Conserver `AgentAllocator` avec lookup par spécialisation | Coupling fort orchestrateur/registry, coomplexe à synchroniser en distribué |
| Routing par header Kafka (taskName dans le header) | Nécessite une logique de partitionnement — complexité sans gain sur le filtrage côté agent |

# ADR-022 — Task Executor Doctrine: Dedicated vs Shell

**Status** : ACCEPTED
**Date** : 2026-07-13
**Deciders** : System Designer, Architect

## Context

8 TaskExecutors exist. Question recurrente : "pourquoi ne pas tout faire via Shell ?"

La plateforme dispose d'un ShellTaskExecutor capable d'executer n'importe quelle
commande shell. En theorie, toutes les operations (base de donnees, Docker,
filesystem, HTTP, Kafka, mock server) pourraient etre realisees via des commandes
shell. Pourtant, 7 executors dedies coexistent avec le ShellTaskExecutor.

Un audit des 8 executors a ete realise pour identifier d'eventuelles duplications.
Aucune duplication fonctionnelle n'a ete constatee : chaque executor dedie couvre
un domaine specifique avec des garde-fous propres.

## Decision

**Executors dedies** : pour operations critiques avec garde-fous (validation,
lifecycle, sandboxing). Chaque executor dedie encapsule :

- **Validation des parametres** : validation semantique specifique au domaine
  (ex: validation de noms de table SQL contre injection, validation d'images
   Docker, validation de chemins filesystem)
- **Gestion du lifecycle** : suivi des ressources creees (connexions, conteneurs,
  fichiers, processus) avec nettoyage automatique via StatefulResourceCleaner
- **Sandboxing** : isolation des operations a risque (ex: FilesystemTaskExecutor
  trace les chemins crees pour cleanup, DatabaseTaskExecutor valide les noms de
  table, DockerTaskExecutor suit les conteneurs par execution)
- **Timeout et resilience** : chaque executor gere ses propres timeouts et
  strategies de retry adaptees au domaine
- **Observabilite** : logging structure specifique au domaine avec les bons
  attributs (table, containerId, path, etc.)

**ShellTaskExecutor** : pour one-off, prototypage, commandes sans SDK Java
disponible. C'est le fallback universel : toute operation qui n'a pas d'executor
dedie peut etre realisee via shell. Le ShellTaskExecutor offre :
- Demarrage de processus avec timeout natif via Process.waitFor()
- Capture stdout/stderr concurrente sous Virtual Threads
- Nettoyage des processus enfants (StatefulResourceCleaner)
- Support de working directory, variables d'environnement, et codes de sortie
  configurables

**Criteres de decision** pour choisir entre dedie et shell :

| Critere | Executeur dedie | ShellTaskExecutor |
|---|---|---|
| Validation metier requise ? | Oui | Non |
| Lifecycle stateful (connexions, conteneurs) ? | Oui | Non |
| Operation reutilisable et critique ? | Oui | Non |
| Pas de SDK Java disponible ? | Non | Oui |
| Prototypage rapide / one-off ? | Non | Oui |
| Commande existante sans wrapper Java ? | Non | Oui |

**Regle de pouce** : si l'operation est critique (base de production, donnees
d'entreprise) ou recurrente (test de charge standard), un executor dedie est
justifie. Si c'est une commande ad-hoc ou un prototype, le ShellTaskExecutor
suffit.

## Consequences

- ShellTaskExecutor reste le fallback universel — aucune operation n'est
  impossible sur la plateforme tant qu'une commande shell existe
- Chaque executor dedie documente ses garde-fous dans sa Javadoc (validation,
  lifecycle, sandboxing)
- Pas de suppression : l'audit n'a trouve aucune duplication fonctionnelle
- Les nouveaux contributeurs disposent d'un arbre de decision clair (criteria
  ci-dessus) pour decider si un nouvel executor dedie est necessaire
- Le package-info.java du package executor liste tous les executors disponibles
  avec leurs cas d'usage, facilitant la decouverte

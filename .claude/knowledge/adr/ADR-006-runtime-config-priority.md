# ADR-006 — Priorité de Configuration Runtime (Properties > Env Vars)

**Date** : 2026-06-08
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : Le mode runtime (LOCAL/DISTRIBUTED/ORCHESTRATOR/AGENT) peut être
configuré via variable d'environnement OU via properties. Une règle de priorité
explicite est nécessaire pour éviter un comportement imprévisible.

---

## Décision

**Les variables d'environnement ont la priorité absolue sur les properties.**

Ordre de résolution (du plus prioritaire au moins prioritaire) :
1. Variable d'environnement
2. `application.yaml` / `application.properties` (fichier de config)
3. Valeur par défaut codée (`LOCAL` pour `runtime.mode`, `AGENT` pour le rôle)

## Mécanisme d'Implémentation

```yaml
# application.yaml — valeurs par défaut, surchargées par les variables d'env
runtime:
  mode: ${RUNTIME_MODE:LOCAL}      # env var RUNTIME_MODE prioritaire, défaut LOCAL
  role: ${MODE:LOCAL}              # env var MODE prioritaire, défaut LOCAL (= standalone)
```

```java
// Le @ConditionalOnProperty lit runtime.mode — alimenté depuis yaml ou env
// Pas de logique conditionnelle sur les variables d'env dans le code Java

@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
public class LocalExecutionEngine implements ExecutionEngine { ... }

@ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
public class RemoteExecutionEngine implements ExecutionEngine { ... }
```

Conséquence concrète :
- Si `application.yaml` contient `runtime.mode: LOCAL` et que `RUNTIME_MODE=DISTRIBUTED`
  est dans l'environnement → **DISTRIBUTED gagne** (l'env surcharge le yaml).
- Si aucune env var n'est définie et que `application.yaml` contient `runtime.mode: LOCAL`
  → **LOCAL actif** (fallback properties).

## Justification

- **Docker-friendly** : `docker run -e RUNTIME_MODE=AGENT` suffit pour changer le mode
  sans monter un fichier de config ou rebuilder l'image.
- **K8s-friendly** : les ConfigMaps et Secrets sont injectés comme variables d'env —
  priorité naturelle sans surcharge de configuration.
- Convention Spring : `${ENV_VAR:default}` dans le yaml donne déjà la priorité à l'env.
- Les tests Spring Boot utilisent `@SpringBootTest(properties=...)` qui reste prioritaire
  sur les env vars système — les tests ne sont pas impactés.

## Conséquences

- Les variables d'env `MODE` et `RUNTIME_MODE` sont les **overrides prioritaires**.
- `application.yaml` contient les valeurs par défaut (mode développement local).
- La documentation doit mentionner explicitement cet ordre.
- Pattern recommandé : `application.yaml` avec `LOCAL` pour le dev, env vars pour tout
  déploiement conteneurisé.

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Properties prioritaires | Rend les overrides Docker/K8s verbeux (montage de fichier config obligatoire) |
| Un seul mécanisme (properties only) | Trop rigide pour les déploiements Docker sans volume |

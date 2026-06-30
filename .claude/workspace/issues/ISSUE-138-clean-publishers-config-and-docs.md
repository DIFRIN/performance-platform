# ISSUE-138 — Nettoyer la config publishers et documenter la diffusion CI/CD

**PDR** : PDR-031
**Module** : `platform-app` (+ doc déploiement)
**Statut** : APPROVED
**Priorité** : P2 (normal)
**Bloquée par** : ISSUE-135
**Estime** : S (< 1h)

---

## Objectif

Retirer les blocs de configuration `platform.publishers` des fichiers YAML de `platform-app`,
retirer la section publishers de la documentation (README / déploiement), et ajouter la
**recommandation CI/CD** : la plateforme génère les fichiers locaux, la diffusion (S3, Git,
Confluence…) est faite par le pipeline sur les fichiers générés (ADR-022).

## Fichiers à Créer / Modifier

```
MODIFIER :
platform-app/src/main/resources/application.yaml                 — retirer bloc platform.publishers
platform-app/src/main/resources/application-orchestrator.yaml    — retirer bloc platform.publishers
platform-deployment/.../README*.md ou doc déploiement            — retirer section publishers,
                                                                   ajouter note diffusion CI/CD
(README racine examples si section publishers présente)
```

## Interfaces à Implémenter

> Aucune interface. Recommandation documentaire (ADR-022) :

```bash
# La plateforme génère les rapports dans ./reports ; le pipeline les diffuse.
java -jar performance-platform.jar --scenario=scenario.yaml
aws s3 cp ./reports/ s3://my-bucket/reports/ --recursive   # diffusion = responsabilité CI/CD
```

## Règles Spécifiques

- Retirer **tout** bloc `platform.publishers:` (actif ou commenté) des YAML cités.
- **Conserver** `reporting.output-directory` et `reporting.formats` (HTML/PDF/JSON) — seule
  sortie supportée.
- `grep -rn "platform.publishers" platform-app platform-deployment` → doit être vide après.
- Doc : remplacer la section « Publishers (Confluence/S3/Git) » par une courte section
  « Diffusion des rapports (CI/CD) » avec l'exemple ci-dessus.

## Critères de Done

- [ ] `grep -rn "platform.publishers" platform-app platform-deployment` → vide
- [ ] `application.yaml` / `application-orchestrator.yaml` : plus de bloc publishers ;
      `reporting.output-directory` + `reporting.formats` conservés
- [ ] Doc : section publishers retirée, note diffusion CI/CD ajoutée
- [ ] `mvn test -pl platform-app -q` → 0 erreur (config valide au démarrage)
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
</content>

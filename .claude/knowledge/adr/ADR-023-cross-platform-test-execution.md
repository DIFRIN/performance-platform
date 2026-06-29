# ADR-023 — Exécution des tests cross-platform (skip Testcontainers sans Docker)

**Date** : 2026-06-29
**Statut** : ACCEPTED
**Décideurs** : Architect (décision utilisateur)

---

## Contexte

Tous les tests d'intégration (`*IT.java`) reposent sur Testcontainers (PostgreSQL, Kafka,
RabbitMQ) **sans aucune garde de disponibilité Docker**. Sur un poste de développement sans
démon Docker (typiquement Windows sans Docker Desktop, ou un runner CI restreint), ces tests
**échouent** au démarrage du conteneur (`IllegalStateException: Could not find a valid Docker
environment`) au lieu d'être proprement **ignorés**.

Tests concernés (recensés) :

| Module | IT |
|---|---|
| `platform-infrastructure` | `DatabaseTaskExecutorIT`, `KafkaTaskExecutorsIT`, `EntitiesMappingIT`, `JpaExecutionRepositoryIT` |
| `platform-transport` | `KafkaExecutionTransportIT`, `RabbitMQExecutionTransportIT` |
| `platform-assertion` | `DatabaseAssertionExecutorIT` |

Aujourd'hui ces classes portent `@Testcontainers` (et `@Tag("integration-tests")`) mais pas
le drapeau `disabledWithoutDocker`.

Par ailleurs, certains tests référencent des chemins POSIX en dur (`/tmp`). La plupart sont
inoffensifs car ils ne sont que des **valeurs de `Path` en mémoire** jamais ouvertes en I/O
(ex. `Path.of("/tmp/gatling")` dans les records de rapport). Un cas réel est en revanche
sensible : `FileAssertionExecutorTest` utilise `"/tmp"` avec un check `EXISTS` qui touche le
système de fichiers — `/tmp` n'existe pas sur Windows, le test échoue.

> Note : `FilesystemTaskExecutor` (code de prod) n'a pas de chemin en dur — il utilise les
> chemins fournis en paramètre. Ses tests utilisent déjà `@TempDir`. Aucun correctif requis
> côté `FilesystemTaskExecutor`.

---

## Décision

**1. Garde Docker canonique sur tous les IT Testcontainers.** Chaque `*IT.java` utilisant
Testcontainers porte :

```java
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class XxxIT { ... }
```

`disabledWithoutDocker = true` est le mécanisme officiel de l'extension JUnit 5 de
Testcontainers : si aucun environnement Docker valide n'est détecté, **toute la classe est
marquée `disabled` (skipped)**, jamais en erreur. Aucun code de détection maison.

**2. Chemins de test portables.** Tout test qui effectue une I/O filesystem réelle doit
utiliser `@TempDir` (JUnit 5) ou `System.getProperty("java.io.tmpdir")`, jamais un littéral
POSIX (`/tmp`, `/var/...`). Les `Path.of("/tmp/...")` qui ne servent que de valeur en mémoire
(jamais ouverts) peuvent rester, mais on privilégie une constante neutre quand on y touche.
Le cas prioritaire à corriger est `FileAssertionExecutorTest` (`"/tmp"` + check `EXISTS`).

**3. Politique générale.** Les tests unitaires (`*Test.java`) doivent rester verts sur
n'importe quelle plateforme **sans Docker** (`mvn test`). Les IT (`*IT.java`) requièrent
Docker ; sans Docker ils sont **skipped**, pas en échec. La CI « complète » (avec Docker)
exécute les IT via le profil `-P integration-tests`.

**4. Tests dépendant d'un shell Unix → garde OS.** Les tests qui invoquent un shell ou des
binaires POSIX (`bash`, `echo`, `touch`, `sleep`, `pwd`, redirections `>&2`, etc.) ne sont
**pas portables sur Windows** (pas de `bash`, pas de `/bin/sh`). Ces tests portent
`@DisabledOnOs(OS.WINDOWS)` (JUnit 5) — ils restent exécutés sur Linux/macOS (et en CI) et
sont **skipped** proprement sur Windows, jamais en erreur.

```java
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)   // dépend de bash / commandes POSIX — non portable Windows
@Test
void shouldCaptureStderr() { ... }
```

Cas prioritaire : `ShellTaskExecutorTest` (`platform-infrastructure`) — la majorité de ses
tests lancent `bash`/`echo`/`touch`/`sleep`/`pwd`. Les tests purement logiques (validation
de paramètres : `command` manquant/blanc, `timeout` négatif, NPE contexte null) restent
exécutés partout. Les tests qui exécutent réellement un process shell portent
`@DisabledOnOs(OS.WINDOWS)`. La garde peut être posée au niveau d'une classe interne
`@Nested` quand tout le groupe est concerné.

> Le code de **production** `ShellTaskExecutor` n'est pas modifié : il exécute la commande
> fournie par le scénario (responsabilité de l'auteur du scénario quant à la portabilité).
> Seuls ses **tests** reçoivent la garde.

**5. `workingDirectory` / chemins de travail des tests shell.** Tout test passant un
`workingDirectory` (ou écrivant un fichier) doit utiliser `@TempDir` plutôt qu'un littéral
`/tmp`. Le test « pwd dans workingDirectory » est soit basé sur `@TempDir` (chemin portable),
soit gardé `@DisabledOnOs(OS.WINDOWS)` s'il dépend par ailleurs de `bash`.

**6. Encodage UTF-8 explicite (règle permanente).** Toute conversion `byte[] ↔ String` et
toute I/O texte (production ou test) précise un charset explicite — **jamais** le charset
plateforme par défaut (`Charset.defaultCharset()`, `new String(bytes)` sans charset,
`getBytes()` sans charset, `FileReader`/`FileWriter`). On utilise `StandardCharsets.UTF_8` (ou
un paramètre `Charset` explicite). Cette règle protège la lecture de la sortie des process
(stdout/stderr de `ProcessBuilder`, dont l'encodage par défaut diffère entre Windows et Linux)
et la lecture de fichiers.

> État constaté : la production est déjà conforme (`ShellTaskExecutor`, renderers de
> `platform-reporting` utilisent `StandardCharsets.UTF_8` ; root pom force
> `project.build.sourceEncoding=UTF-8`). Cette décision **fige la règle** pour tout nouveau
> code et impose la correction de tout point non conforme rencontré.

---

## Justification

- **Solution standard, zéro code maison** : `disabledWithoutDocker` est la réponse officielle
  Testcontainers ; pas d'`Assumptions.assumeTrue(...)` dispersés.
- **Build vert partout** : un développeur Windows sans Docker exécute `mvn test` et obtient
  un build vert (IT skipped), pas une mer de rouge.
- **Portabilité filesystem** : `@TempDir` garantit un répertoire temporaire valide et nettoyé
  sur tout OS.

---

## Conséquences

**Positives** :
- `mvn test` vert sur Windows/macOS/Linux sans Docker (IT skipped explicitement).
- Distinction nette « test unitaire (toujours exécuté) » vs « IT (nécessite Docker) ».

**Négatives / Contraintes** :
- Sur un environnement sans Docker, les IT n'apportent aucune couverture (skipped) — c'est
  voulu ; la couverture IT reste obligatoire en CI avec Docker.
- Il faut veiller à ce que tout nouvel `*IT.java` Testcontainers porte la garde (à rappeler
  dans la skill testing-strategy).

**Fichiers impactés** :
- 7 `*IT.java` (3 modules) — ajout de `disabledWithoutDocker = true`.
- `platform-assertion/.../FileAssertionExecutorTest.java` — `"/tmp"` → `@TempDir`.
- `platform-infrastructure/.../executor/shell/ShellTaskExecutorTest.java` — `@DisabledOnOs(OS.WINDOWS)`
  sur les tests lançant un process shell + `workingDirectory` `/tmp` → `@TempDir`.
- (Encodage) tout point de conversion `byte[]↔String` / I/O texte non conforme rencontré
  doit passer à `StandardCharsets.UTF_8` — production déjà conforme à ce jour.
- `.claude/knowledge/skills/testing-strategy.md` — rappel des gardes (Docker + shell OS +
  encodage UTF-8) (note, hors périmètre d'écriture du Developer pour cet ADR ; rappelé dans
  les Issues).

---

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| `Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable())` dans chaque test | Verbeux, dupliqué, et s'exécute après l'init du conteneur ; `disabledWithoutDocker` est plus propre et au niveau classe. |
| Exclure les `*IT` par défaut via surefire et ne les lancer qu'en CI | Déjà le cas (failsafe + profil) ; ne règle pas le cas où l'on lance volontairement les IT sans Docker. La garde rend le skip explicite et auto-documenté. |
| Imposer Docker Desktop sur tous les postes | Contrainte lourde et non universelle (licences, droits admin) ; viole l'objectif « build vert partout ». |

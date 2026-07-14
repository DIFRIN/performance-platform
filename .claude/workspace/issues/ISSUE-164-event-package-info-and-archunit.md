# ISSUE-164 — Event package-info.java + ArchUnit Test

**PDR** : PDR-040
**Module** : `platform-domain`
**Statut** : DONE
**Priorite** : P2 (documentation + test de non-régression)
**Bloquee par** : ISSUE-163 (interfaces scellées créées)
**Estime** : S (< 1h)

---

## Objectif

Ajouter un `package-info.java` documentant les 4 catégories d'événements et un test ArchUnit qui vérifie que la hiérarchie scellée reste cohérente dans le temps.

## Fichiers à Créer

```
platform-domain/src/main/java/com/performance/platform/domain/event/
  └── package-info.java            — documentation des catégories

platform-domain/src/test/java/com/performance/platform/domain/event/
  └── EventHierarchyArchTest.java  — NOUVEAU: test ArchUnit
```

## package-info.java

Documenter les 4 catégories (ExecutionEvent, TaskEvent, AssertionEvent, AgentSignal) avec une table. Voir le contenu exact dans PDR-040.

## EventHierarchyArchTest

```java
package com.performance.platform.domain.event;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class EventHierarchyArchTest {

    private final JavaClasses eventClasses = new ClassFileImporter()
            .importPackages("com.performance.platform.domain.event");

    @Test
    void allEventRecordsMustImplementExactlyOneSealedInterface() {
        classes()
                .that().areRecords()
                .and().resideInAPackage("com.performance.platform.domain.event")
                .should(new ArchCondition<>("implement exactly one sealed event interface") {
                    @Override
                    public void check(JavaClass record, ConditionEvents events) {
                        // Exclure les interfaces elles-mêmes et AgentSignal/ScenarioRestartSignal
                        // Vérifier que le record implémente exactement 1 des 3 interfaces
                        // (ou AgentSignal pour ScenarioRestartSignal)
                    }
                })
                .check(eventClasses);
    }

    @Test
    void sealedInterfacesPermitsMustBeExhaustive() {
        // Vérifier que ExecutionEvent permits contient exactement les 7 records
        // Vérifier que TaskEvent permits contient exactement les 7 records
        // Vérifier que AssertionEvent permits contient exactement les 2 records
    }
}
```

## Règles Spécifiques

- ArchUnit est déjà une dépendance de test dans `platform-domain` (utilisé pour la règle "0 annotation Spring/JPA")
- Le test vérifie que TOUS les records du package `event` implémentent une (et une seule) des interfaces scellées
- Exclusion : `AgentSignal`, `ScenarioRestartSignal`, et les 3 nouvelles interfaces elles-mêmes
- Le test de couverture (`permits` exhaustifs) garantit qu'un nouvel event ne peut pas être ajouté sans mettre à jour l'interface scellée correspondante

## Criteres de Done

- [ ] `package-info.java` créé avec documentation des catégories
- [ ] `EventHierarchyArchTest` créé avec tests de cohérence
- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] Le test détecterait un event orphelin (sans interface) ou un `permits` désynchronisé

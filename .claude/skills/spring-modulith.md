# Skill — Spring Modulith

> Règles d'utilisation de Spring Modulith dans ce projet.

---

## Principe

Chaque module Spring Modulith = un package de premier niveau sous
`com.performance.platform`. Les modules ne peuvent PAS s'appeler directement.

## Communication Inter-Modules

```java
// ✅ CORRECT : via ApplicationEventPublisher
@Component
public class ExecutionEngine {
    private final ApplicationEventPublisher events;

    public void completeTask(TaskResult result) {
        events.publishEvent(new TaskCompleted(result));
    }
}

// ✅ CORRECT : listener dans un autre module
@ApplicationModuleListener
public class ReportingEventListener {
    @EventListener
    public void on(TaskCompleted event) { ... }
}

// ❌ INTERDIT : injection directe entre modules
@Component
public class ExecutionEngine {
    private final ReportingService reporting;  // ← violation !
}
```

## Test de Module Isolé

```java
@ApplicationModuleTest
class ExecutionModuleTest {
    // Démarre uniquement le module execution + ses dépendances déclarées
    // Les autres modules sont mockés
}
```

## Déclaration des Dépendances

```java
// Dans le package-info.java de chaque module :
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"scenario", "domain"}
)
package com.performance.platform.execution;
```

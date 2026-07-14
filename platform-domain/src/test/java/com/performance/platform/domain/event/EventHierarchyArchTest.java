package com.performance.platform.domain.event;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ArchUnit test verifying the event hierarchy coherence in the domain event package.
 *
 * <p>Ensures that:
 * <ul>
 *   <li>Every record that implements a sealed event interface implements exactly one.</li>
 *   <li>The {@code permits} clause of each sealed interface is exhaustive
 *       (no missing or extra entries at runtime).</li>
 * </ul>
 *
 * <p>Adding a new event record without updating the corresponding sealed interface
 * (or adding one without adding to {@code permits}) will cause this test to fail.
 */
@DisplayName("Event Hierarchy — sealed interface coherence")
class EventHierarchyArchTest {

    private static final String EVENT_PACKAGE = "com.performance.platform.domain.event";

    private static final Set<String> SEALED_INTERFACES = Set.of(
            EVENT_PACKAGE + ".ExecutionEvent",
            EVENT_PACKAGE + ".TaskEvent",
            EVENT_PACKAGE + ".AssertionEvent",
            EVENT_PACKAGE + ".AgentSignal"
    );

    /**
     * Standalone records in the event package that do not currently implement
     * any sealed event interface. They represent agent lifecycle state changes
     * (registration, loss, recovery) and are accepted as exceptions.
     */
    private static final Set<String> STANDALONE_RECORDS = Set.of(
            EVENT_PACKAGE + ".AgentRegistered",
            EVENT_PACKAGE + ".AgentLost",
            EVENT_PACKAGE + ".AgentRecovered"
    );

    private static JavaClasses eventClasses;

    @BeforeAll
    static void loadClasses() {
        var classesPath = Path.of("target", "classes");
        eventClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath(classesPath);
    }

    @Test
    @DisplayName("every event record implements at most one sealed interface")
    void allEventRecordsMustImplementExactlyOneSealedInterface() {
        classes()
                .that().resideInAPackage(EVENT_PACKAGE)
                .should(new ArchCondition<>("implement at most one sealed event interface") {
                    @Override
                    public void check(JavaClass clazz, ConditionEvents events) {
                        // Skip non-record types (interfaces, enums)
                        if (!clazz.isRecord()) {
                            return;
                        }

                        long count = countSealedInterfaces(clazz);

                        if (count > 1) {
                            events.add(SimpleConditionEvent.violated(clazz,
                                    "%s implements %d sealed interfaces, expected 0 or 1"
                                            .formatted(clazz.getSimpleName(), count)));
                        }
                        // count == 0 is acceptable for known standalone records,
                        // but any new record without a sealed interface is a violation.
                        if (count == 0 && !STANDALONE_RECORDS.contains(clazz.getName())) {
                            events.add(SimpleConditionEvent.violated(clazz,
                                    "%s is a record with no sealed event interface. "
                                            + "It must implement one of ExecutionEvent, TaskEvent, AssertionEvent, or AgentSignal."
                                            .formatted(clazz.getSimpleName())));
                        }
                    }
                })
                .allowEmptyShould(true)
                .check(eventClasses);
    }

    @Test
    @DisplayName("sealed interface permits clauses are exhaustive")
    void sealedInterfacesPermitsMustBeExhaustive() throws ClassNotFoundException {
        // ExecutionEvent — 7 records
        assertPermitsExhaustive("ExecutionEvent", Set.of(
                "ScenarioStarted", "ScenarioFinished", "ScenarioCancelled",
                "PhaseStarted", "PhaseCompleted", "ReportGenerated", "ReportPublished"));

        // TaskEvent — 7 records
        assertPermitsExhaustive("TaskEvent", Set.of(
                "TaskStarted", "TaskCompleted", "TaskFailed", "TaskRetried",
                "TaskDispatched", "TaskClaimedByAgent", "TaskWorkInProgress"));

        // AssertionEvent — 2 records
        assertPermitsExhaustive("AssertionEvent", Set.of(
                "AssertionPassed", "AssertionFailed"));

        // AgentSignal — 2 records
        assertPermitsExhaustive("AgentSignal", Set.of(
                "ScenarioRestartSignal", "ExecutionLifecycleSignal"));
    }

    /**
     * Counts how many of the 4 sealed event interfaces the given class implements.
     */
    private static long countSealedInterfaces(JavaClass clazz) {
        return clazz.getInterfaces().stream()
                .filter(iface -> SEALED_INTERFACES.contains(iface.getName()))
                .count();
    }

    /**
     * Asserts that the {@code permits} clause of the given sealed interface
     * matches the expected set of record simple names.
     *
     * <p>Uses {@link Class#getPermittedSubclasses()} at runtime to read the
     * actual permits list declared in source.
     */
    private void assertPermitsExhaustive(String interfaceName, Set<String> expectedSimpleNames)
            throws ClassNotFoundException {

        Class<?> iface = Class.forName(EVENT_PACKAGE + "." + interfaceName);
        Class<?>[] permitted = iface.getPermittedSubclasses();

        Set<String> actual = Arrays.stream(permitted)
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertEquals(expectedSimpleNames, actual,
                "Permits clause mismatch for " + interfaceName
                        + ". If you added a new event record, update the permits clause.");
    }
}

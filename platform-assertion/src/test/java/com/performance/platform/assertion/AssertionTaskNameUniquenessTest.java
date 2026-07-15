package com.performance.platform.assertion;

import com.performance.platform.plugin.Assertion;
import com.performance.platform.plugin.Injection;
import com.performance.platform.plugin.Preparation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie qu'aucun nom d'assertion ({@code @Assertion}) n'entre
 * en conflit avec un nom de tache standard ({@code @Preparation}
 * ou {@code @Injection}).
 *
 * <p>Les assertions sont des {@link com.performance.platform.plugin.TaskExecutor}
 * et partagent le meme namespace de noms. Un conflit de nom entrainerait
 * l'ecrasement silencieux d'un executor par l'autre dans le registre
 * unifie {@code DefaultTaskExecutorRegistry}.</p>
 */
@DisplayName("Assertion Task Name Uniqueness Test")
class AssertionTaskNameUniquenessTest {

    private static final String PREPARATION_INJECTION_PKG =
            "com.performance.platform.infrastructure.executor";
    private static final String ASSERTION_PKG =
            "com.performance.platform.assertion";

    @Test
    @DisplayName("should have no assertion name conflicting with preparation/injection task names")
    void shouldHaveNoConflictingNames() {
        Set<String> assertionNames = scanAssertionNames();
        Set<String> taskNames = scanTaskNames();

        Set<String> conflicts = new HashSet<>(assertionNames);
        conflicts.retainAll(taskNames);

        assertThat(conflicts)
                .as("Assertion names must not overlap with @Preparation/@Injection task names. "
                    + "Conflicts found: %s. Rename the conflicting assertion executor.",
                    conflicts)
                .isEmpty();
    }

    @Test
    @DisplayName("should have unique assertion names among all assertion executors")
    void shouldHaveUniqueAssertionNames() {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Assertion.class));

        Set<String> seen = new HashSet<>();
        for (BeanDefinition bd : provider.findCandidateComponents(ASSERTION_PKG)) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                Assertion annotation = clazz.getAnnotation(Assertion.class);
                if (annotation != null) {
                    String name = annotation.name();
                    assertThat(seen)
                            .as("Duplicate assertion name '%s' in %s", name, clazz.getSimpleName())
                            .doesNotContain(name);
                    seen.add(name);
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Cannot load class: " + bd.getBeanClassName(), e);
            }
        }

        assertThat(seen).isNotEmpty();
    }

    private Set<String> scanAssertionNames() {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Assertion.class));

        Set<String> names = new HashSet<>();
        for (BeanDefinition bd : provider.findCandidateComponents(ASSERTION_PKG)) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                Assertion annotation = clazz.getAnnotation(Assertion.class);
                if (annotation != null) {
                    names.add(annotation.name());
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Cannot load class: " + bd.getBeanClassName(), e);
            }
        }
        return names;
    }

    private Set<String> scanTaskNames() {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Preparation.class));
        provider.addIncludeFilter(new AnnotationTypeFilter(Injection.class));

        Set<String> names = new HashSet<>();
        for (BeanDefinition bd : provider.findCandidateComponents(PREPARATION_INJECTION_PKG)) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                Preparation prep = clazz.getAnnotation(Preparation.class);
                if (prep != null) {
                    names.add(prep.name());
                }
                Injection inj = clazz.getAnnotation(Injection.class);
                if (inj != null) {
                    names.add(inj.name());
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Cannot load class: " + bd.getBeanClassName(), e);
            }
        }
        return names;
    }
}

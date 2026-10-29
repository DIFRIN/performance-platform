package com.performance.platform.infrastructure.plugin;

import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.plugin.Assertion;
import com.performance.platform.plugin.Injection;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Implementation par defaut de {@link AnnotationScanner} basee sur la reflexion Java.
 *
 * <p>Detecte les annotations {@code @Preparation}, {@code @Injection}, {@code @Assertion}
 * via {@link Class#isAnnotationPresent(Class)} et extrait les metadonnees.</p>
 *
 * <p>Reflexion pure — la methode {@link #scan(Class)} n'utilise aucune API Spring.
 * La classe est un bean Spring uniquement pour l'injection dans {@link DefaultPluginLoader}
 * et {@link DefaultPluginRegistry}.</p>
 */
@Component
public class DefaultAnnotationScanner implements AnnotationScanner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAnnotationScanner.class);

    @Override
    public Optional<PluginDescriptor> scan(Class<?> candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");

        boolean isPreparation = candidate.isAnnotationPresent(Preparation.class);
        boolean isInjection = candidate.isAnnotationPresent(Injection.class);
        boolean isAssertion = candidate.isAnnotationPresent(Assertion.class);

        int annotationCount = countAnnotations(isPreparation, isInjection, isAssertion);

        if (annotationCount == 0) {
            return Optional.empty();
        }

        if (annotationCount > 1) {
            log.warn("action=multiple_plugin_annotations class={} preparation={} injection={} assertion={}",
                    candidate.getName(), isPreparation, isInjection, isAssertion);
            return Optional.empty();
        }

        // annotationCount == 1 — exactly one annotation
        return Optional.of(buildDescriptor(candidate, isPreparation, isInjection, isAssertion));
    }

    /**
     * Compte le nombre d'annotations de plugin presentes.
     */
    private int countAnnotations(boolean preparation, boolean injection, boolean assertion) {
        return (preparation ? 1 : 0) + (injection ? 1 : 0) + (assertion ? 1 : 0);
    }

    /**
     * Construit un {@link PluginDescriptor} a partir de l'unique annotation detectee.
     *
     * @param candidate      la classe candidate
     * @param isPreparation  true si {@code @Preparation} est presente
     * @param isInjection    true si {@code @Injection} est presente
     * @param isAssertion    true si {@code @Assertion} est presente
     * @return le {@link PluginDescriptor} correspondant
     */
    @SuppressWarnings("unchecked")
    private PluginDescriptor buildDescriptor(Class<?> candidate,
                                             boolean isPreparation,
                                             boolean isInjection,
                                             boolean isAssertion) {
        Class<? extends TaskExecutor> executorClass = (Class<? extends TaskExecutor>) candidate;

        if (isPreparation) {
            Preparation prep = candidate.getAnnotation(Preparation.class);
            return new PluginDescriptor(
                    prep.name(), prep.version(), prep.description(),
                    Phase.PREPARATION, executorClass);
        }
        if (isInjection) {
            Injection inj = candidate.getAnnotation(Injection.class);
            return new PluginDescriptor(
                    inj.name(), inj.version(), inj.description(),
                    Phase.INJECTION, executorClass);
        }
        if (isAssertion) {
            Assertion asrt = candidate.getAnnotation(Assertion.class);
            return new PluginDescriptor(
                    asrt.name(), asrt.version(), asrt.description(),
                    Phase.ASSERTION, executorClass);
        }

        // Should never reach here (annotationCount == 1 guarantee)
        throw new IllegalStateException(
                "Unexpected: no annotation matched for class " + candidate.getName());
    }
}

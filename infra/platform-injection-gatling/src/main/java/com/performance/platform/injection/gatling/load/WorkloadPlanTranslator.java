package com.performance.platform.injection.gatling.load;

import com.performance.platform.domain.injection.WorkloadPlan;
import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.OpenInjectionStep;

import java.util.List;

/** Traduit un {@link WorkloadPlan} en etapes d'injection Gatling. Sans etat, thread-safe. */
public interface WorkloadPlanTranslator {

    /** @param plan plan de modele OPEN */
    List<OpenInjectionStep> toOpenSteps(WorkloadPlan plan);

    /** @param plan plan de modele CLOSED */
    List<ClosedInjectionStep> toClosedSteps(WorkloadPlan plan);
}

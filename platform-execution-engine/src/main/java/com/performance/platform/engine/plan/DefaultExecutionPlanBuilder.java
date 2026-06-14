package com.performance.platform.engine.plan;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionPlan;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation par defaut de {@link ExecutionPlanBuilder}.
 *
 * <p>Transforme un {@link ScenarioDefinition} valide en {@link ExecutionPlan} en :
 * <ol>
 *   <li>Calculant le dagLevel de chaque etape via {@link DagLevelCalculator}</li>
 *   <li>Creant un {@link ExecutionStep} par {@link StepDefinition}</li>
 *   <li>Separrant les etapes par phase (PREPARATION / INJECTION / ASSERTION)</li>
 *   <li>Tirant chaque phase par dagLevel croissant</li>
 *   <li>Initialisant le {@link ExecutionContext} de depart</li>
 * </ol>
 *
 * <p>Leve {@link com.performance.platform.application.exception.InvalidScenarioException}
 * si un cycle est detecte (delegue a {@link DagLevelCalculator}).</p>
 */
@Component
public class DefaultExecutionPlanBuilder implements ExecutionPlanBuilder {

    private static final Comparator<ExecutionStep> BY_DAG_LEVEL =
            Comparator.comparingInt(ExecutionStep::dagLevel);

    @Override
    public ExecutionPlan build(ScenarioDefinition scenario) {
        List<StepDefinition> stepDefs = scenario.steps();
        if (stepDefs == null) {
            stepDefs = List.of();
        }

        // 1. Calcul global des niveaux DAG (toutes phases confondues)
        Map<TaskId, Integer> dagLevels = DagLevelCalculator.compute(stepDefs);

        // 2. Creation des ExecutionStep
        List<ExecutionStep> allSteps = new ArrayList<>(stepDefs.size());
        for (StepDefinition stepDef : stepDefs) {
            int dagLevel = dagLevels.getOrDefault(stepDef.id(), 0);
            Set<String> requiredContextKeys = stepDef.requiredContexts() == null
                    ? Set.of()
                    : Set.copyOf(stepDef.requiredContexts());

            ExecutionStep execStep = new ExecutionStep(
                    stepDef,
                    stepDef.dependsOn(),
                    dagLevel,
                    requiredContextKeys
            );
            allSteps.add(execStep);
        }

        // 3. Separation par phase
        List<ExecutionStep> preparationSteps = new ArrayList<>();
        List<ExecutionStep> injectionSteps = new ArrayList<>();
        List<ExecutionStep> assertionSteps = new ArrayList<>();

        for (ExecutionStep step : allSteps) {
            switch (step.step().phase()) {
                case PREPARATION -> preparationSteps.add(step);
                case INJECTION -> injectionSteps.add(step);
                case ASSERTION -> assertionSteps.add(step);
            }
        }

        // 4. Tri par dagLevel croissant au sein de chaque phase
        preparationSteps.sort(BY_DAG_LEVEL);
        injectionSteps.sort(BY_DAG_LEVEL);
        assertionSteps.sort(BY_DAG_LEVEL);

        // 5. Construction du plan avec contexte initial vide
        ExecutionId executionId = ExecutionId.generate();
        return new ExecutionPlan(
                executionId,
                scenario.id(),
                preparationSteps,
                injectionSteps,
                assertionSteps,
                ExecutionContext.initial(executionId, scenario.id())
        );
    }
}

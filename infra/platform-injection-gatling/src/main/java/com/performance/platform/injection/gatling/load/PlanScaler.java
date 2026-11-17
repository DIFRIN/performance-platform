package com.performance.platform.injection.gatling.load;

import com.performance.platform.domain.execution.GrantShare;
import com.performance.platform.domain.injection.ArrivalModel;
import com.performance.platform.domain.injection.Burst;
import com.performance.platform.domain.injection.Hold;
import com.performance.platform.domain.injection.LoadScope;
import com.performance.platform.domain.injection.Pause;
import com.performance.platform.domain.injection.Ramp;
import com.performance.platform.domain.injection.Stage;
import com.performance.platform.domain.injection.WorkloadPlan;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;

/** Met un plan {@code scope: global} a l'echelle de la part d'un agent (ADR-067). */
public final class PlanScaler {

    private PlanScaler() {
    }

    /**
     * PER_AGENT : plan inchange. GLOBAL : niveaux x fraction ; entiers (Burst, population fermee)
     * repartis au plus fort reste selon l'index. Un Burst de part nulle est retire ; si plus aucune
     * etape ne subsiste, le plan est remplace par une pause minimale (voir {@link #isIdle}).
     */
    public static WorkloadPlan scale(WorkloadPlan plan, GrantShare share) {
        Objects.requireNonNull(plan, "plan required");
        Objects.requireNonNull(share, "share required");
        if (plan.scope() != LoadScope.GLOBAL) {
            return plan;
        }
        boolean closed = plan.model() == ArrivalModel.CLOSED;
        var stages = new ArrayList<Stage>();
        for (Stage s : plan.stages()) {
            switch (s) {
                case Ramp r -> stages.add(new Ramp(level(r.from(), closed, share), level(r.to(), closed, share),
                        r.duration(), r.measured()));
                case Hold h -> stages.add(new Hold(level(h.level(), closed, share), h.duration(), h.measured()));
                case Burst b -> {
                    long users = integerShare(b.users(), share);
                    if (users > 0) stages.add(new Burst(users, b.measured()));
                }
                case Pause p -> stages.add(p);
            }
        }
        if (stages.isEmpty()) {
            stages.add(new Pause(Duration.ofMillis(1), false));
        }
        return new WorkloadPlan(plan.model(), plan.scope(), stages);
    }

    /** Vrai si la part de l'agent n'injecte rien sur aucune etape (aucun worker a lancer). */
    public static boolean isIdle(WorkloadPlan scaled) {
        return scaled.stages().stream().noneMatch(s -> switch (s) {
            case Ramp r -> r.from() > 0 || r.to() > 0;
            case Hold h -> h.level() > 0;
            case Burst b -> b.users() > 0;
            case Pause p -> false;
        });
    }

    /** Part entiere de {@code total} : plus fort reste selon l'index (somme des parts = total). */
    public static long integerShare(long total, GrantShare share) {
        Objects.requireNonNull(share, "share required");
        if (total < 0) throw new IllegalArgumentException("total must be >= 0: " + total);
        long base = total / share.count();
        long remainder = total % share.count();
        return base + (share.index() < remainder ? 1 : 0);
    }

    private static double level(double value, boolean closed, GrantShare share) {
        return closed ? integerShare((long) Math.floor(value), share) : value * share.fraction();
    }
}

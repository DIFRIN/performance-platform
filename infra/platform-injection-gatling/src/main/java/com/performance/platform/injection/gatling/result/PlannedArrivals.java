package com.performance.platform.injection.gatling.result;

import com.performance.platform.domain.injection.ArrivalModel;
import com.performance.platform.domain.injection.Burst;
import com.performance.platform.domain.injection.Hold;
import com.performance.platform.domain.injection.Ramp;
import com.performance.platform.domain.injection.Stage;
import com.performance.platform.domain.injection.WorkloadPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Intégrale du débit du plan (modèle ouvert) sur des fenêtres arbitraires. Les comptes par fenêtre
 * sont des différences de l'intégrale cumulée arrondie : leur somme égale le total du plan (±1).
 * Modèle fermé : aucune arrivée connue ({@code arrivalKnown=false}, comptes à 0).
 */
final class PlannedArrivals {

    private record Segment(double startSec, double endSec, double from, double to) {}

    private final boolean arrivalKnown;
    private final List<Segment> segments = new ArrayList<>();
    private final List<double[]> bursts = new ArrayList<>(); // {instantSec, users}
    private final double totalPlanned;
    private final long planStartMs;

    PlannedArrivals(WorkloadPlan plan, long planStartMs) {
        this.planStartMs = planStartMs;
        this.arrivalKnown = plan != null && plan.model() == ArrivalModel.OPEN;
        double cursor = 0;
        double total = 0;
        if (arrivalKnown) {
            for (Stage stage : plan.stages()) {
                double len = stage.duration().toNanos() / 1e9;
                switch (stage) {
                    case Ramp r -> {
                        segments.add(new Segment(cursor, cursor + len, r.from(), r.to()));
                        total += (r.from() + r.to()) / 2 * len;
                    }
                    case Hold h -> {
                        segments.add(new Segment(cursor, cursor + len, h.level(), h.level()));
                        total += h.level() * len;
                    }
                    case Burst b -> {
                        bursts.add(new double[] {cursor, b.users()});
                        total += b.users();
                    }
                    default -> { }
                }
                cursor += len;
            }
        }
        this.totalPlanned = total;
    }

    boolean arrivalKnown() {
        return arrivalKnown;
    }

    /** Total théorique du plan (0 si modèle fermé). */
    double total() {
        return totalPlanned;
    }

    /** Arrivées planifiées dans {@code [startMs, startMs + lengthMs[} (horloge murale). */
    long inWindow(long startMs, long lengthMs) {
        if (!arrivalKnown) return 0;
        return Math.round(cumulative(startMs + lengthMs)) - Math.round(cumulative(startMs));
    }

    /** Intégrale du plan depuis son début jusqu'à {@code wallMs} (bursts strictement antérieurs inclus). */
    private double cumulative(long wallMs) {
        double t = (wallMs - planStartMs) / 1000.0;
        if (t <= 0) return 0;
        double sum = 0;
        for (Segment s : segments) {
            if (t <= s.startSec) break;
            double upTo = Math.min(t, s.endSec);
            double len = s.endSec - s.startSec;
            double dt = upTo - s.startSec;
            double levelAtEnd = s.from + (s.to - s.from) * (dt / len);
            sum += (s.from + levelAtEnd) / 2 * dt;
        }
        for (double[] b : bursts) {
            if (b[0] < t) sum += b[1];
        }
        return sum;
    }
}

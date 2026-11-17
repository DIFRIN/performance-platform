package com.performance.platform.domain.assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IntegrityTolerancesTest {

    @Test
    void defaultsAreFiveAndNinety() {
        var d = IntegrityTolerances.defaults();
        assertEquals(5.0, d.maxArrivalDeviationPercent());
        assertEquals(90.0, d.maxGeneratorCpuPercent());
    }

    @Test
    void acceptsUpperBound() {
        new IntegrityTolerances(100.0, 100.0);
    }

    @Test
    void rejectsZeroNegativeAboveHundredAndNaN() {
        assertThrows(IllegalArgumentException.class, () -> new IntegrityTolerances(0, 90));
        assertThrows(IllegalArgumentException.class, () -> new IntegrityTolerances(-1, 90));
        assertThrows(IllegalArgumentException.class, () -> new IntegrityTolerances(5, 100.1));
        assertThrows(IllegalArgumentException.class, () -> new IntegrityTolerances(5, Double.NaN));
    }
}

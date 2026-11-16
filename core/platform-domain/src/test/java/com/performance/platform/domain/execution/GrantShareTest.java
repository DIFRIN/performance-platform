package com.performance.platform.domain.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrantShareTest {

    @Test
    void singleIsWholeShare() {
        assertEquals(new GrantShare(0, 1, 1.0), GrantShare.single());
    }

    @Test
    void equalSplitsFraction() {
        GrantShare s = GrantShare.equal(1, 4);
        assertEquals(1, s.index());
        assertEquals(4, s.count());
        assertEquals(0.25, s.fraction(), 1e-12);
    }

    @Test
    void rejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> new GrantShare(0, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new GrantShare(-1, 2, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new GrantShare(2, 2, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new GrantShare(0, 1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GrantShare(0, 1, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new GrantShare(0, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> GrantShare.equal(0, 0));
    }
}

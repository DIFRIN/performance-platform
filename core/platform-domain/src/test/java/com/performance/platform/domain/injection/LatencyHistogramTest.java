package com.performance.platform.domain.injection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LatencyHistogramTest {

    private static LatencyHistogram of(long[] ok, long[] ko) {
        LatencyHistogram h = LatencyHistogram.empty();
        for (long v : ok) h = h.with(v, true);
        for (long v : ko) h = h.with(v, false);
        return h;
    }

    @Test
    void bucketOfIsIdentityUpTo1000() {
        assertEquals(0, LatencyHistogram.bucketOf(0));
        assertEquals(537, LatencyHistogram.bucketOf(537));
        assertEquals(1000, LatencyHistogram.bucketOf(1000));
    }

    @Test
    void bucketOfRoundsUpToTwoSignificantDigits() {
        assertEquals(1100, LatencyHistogram.bucketOf(1001));
        assertEquals(1100, LatencyHistogram.bucketOf(1100));
        assertEquals(1300, LatencyHistogram.bucketOf(1234));
        assertEquals(13000, LatencyHistogram.bucketOf(12345));
        assertEquals(10000, LatencyHistogram.bucketOf(9999 + 1));
        assertEquals(1_100_000, LatencyHistogram.bucketOf(1_000_001));
    }

    @Test
    void quantileIsSmallestBoundReachingRank() {
        LatencyHistogram h = of(new long[] {10, 20, 30, 40}, new long[0]);
        assertEquals(10, h.quantile(0.25));
        assertEquals(20, h.quantile(0.5));
        assertEquals(40, h.quantile(1.0));
        assertEquals(4, h.count());
    }

    @Test
    void quantileCoversOkAndKo() {
        LatencyHistogram h = of(new long[] {10, 20}, new long[] {500, 600});
        assertEquals(500, h.quantile(0.75));
    }

    @Test
    void quantileRejectsEmptyAndBadQ() {
        assertThrows(IllegalStateException.class, () -> LatencyHistogram.empty().quantile(0.5));
        LatencyHistogram h = of(new long[] {1}, new long[0]);
        assertThrows(IllegalArgumentException.class, () -> h.quantile(0));
        assertThrows(IllegalArgumentException.class, () -> h.quantile(1.1));
    }

    @Test
    void mergeIsCommutativeAndAssociative() {
        LatencyHistogram a = of(new long[] {1, 2, 2000}, new long[] {5});
        LatencyHistogram b = of(new long[] {2, 3}, new long[] {5, 7});
        LatencyHistogram c = of(new long[] {1500}, new long[0]);
        assertEquals(a.merge(b), b.merge(a));
        assertEquals(a.merge(b).merge(c), a.merge(b.merge(c)));
    }

    @Test
    void mergedQuantileEqualsQuantileOnUnion() {
        long[] s1 = {5, 15, 25, 1234, 80, 80};
        long[] s2 = {7, 7, 900, 1500, 20000, 3};
        List<Long> union = new ArrayList<>();
        for (long v : s1) union.add(v);
        for (long v : s2) union.add(v);
        LatencyHistogram all = LatencyHistogram.empty();
        for (long v : union) all = all.with(v, true);
        LatencyHistogram merged = of(s1, new long[0]).merge(of(s2, new long[0]));
        for (double q : new double[] {0.1, 0.5, 0.75, 0.95, 0.99, 1.0}) {
            assertEquals(all.quantile(q), merged.quantile(q), "q=" + q);
        }
    }

    @Test
    void mapRoundTrip() {
        LatencyHistogram h = of(new long[] {1, 2, 2000}, new long[] {5});
        assertEquals(h, LatencyHistogram.fromMap(h.toMap()));
        assertEquals(LatencyHistogram.empty(), LatencyHistogram.fromMap(Map.of()));
    }

    @Test
    void buildersCopyDefensively() {
        java.util.HashMap<Long, Long> m = new java.util.HashMap<>(Map.of(1L, 1L));
        LatencyHistogram h = new LatencyHistogram(m, null);
        m.put(2L, 5L);
        assertEquals(1, h.count());
    }
}
